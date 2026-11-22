/*
 * Copyright (c) 2024 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal;

import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.Installer;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.mockito.internal.util.StringUtil.join;

public class PremainAttachAccess {

    private static volatile Instrumentation inst;

    public static Instrumentation getInstrumentation() {
        if (inst != null) {
            return inst;
        }
        // A Java agent is always added to the system class loader. If Mockito is executed from a
        // different class loader we need to make sure to resolve the instrumentation instance
        // from there, or fail the resolution, if this class does not exist on the system class
        // loader.
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        Instrumentation instrumentation =
                doGetInstrumentation(PremainAttach.class, "getInstrumentation", systemClassLoader);
        if (instrumentation == null) {
            try {
                // At this point, we do not want to attempt dynamic attach but check the Byte Buddy
                // agent as a fallback if the Mockito agent is not attached.
                instrumentation =
                        doGetInstrumentation(
                                Installer.class, "getInstrumentation", systemClassLoader);
            } catch (IllegalStateException ignored) {
            }
        }
        if (instrumentation == null) {
            if (ClassFileVersion.ofThisVm().isAtLeast(ClassFileVersion.JAVA_V21)) {
                boolean dynamicAgentLoading;
                try {
                    Object runtimeMXBean =
                            Class.forName("java.lang.management.ManagementFactory")
                                    .getMethod("getRuntimeMXBean")
                                    .invoke(null);

                    @SuppressWarnings("unchecked")
                    List<String> arguments =
                            (List<String>)
                                    Class.forName("java.lang.management.RuntimeMXBean")
                                            .getMethod("getInputArguments")
                                            .invoke(runtimeMXBean);
                    dynamicAgentLoading =
                            arguments.stream()
                                    .anyMatch(
                                            argument ->
                                                    argument.contains(
                                                            "-XX:+EnableDynamicAgentLoading"));
                } catch (Exception ignored) {
                    dynamicAgentLoading = false;
                }
                if (!dynamicAgentLoading) {
                    // Cannot use `Plugins.getMockitoLogger().warn(...)` at this time due to a
                    // circular dependency on `Plugins.registry`.
                    // The `PluginRegistry` is not yet fully initialized (in `Plugins`), because it
                    // is currently initializing the `MockMaker` which is a
                    // InlineByteBuddyMockMaker, and it is later calling this
                    // method to access the instrumentation.
                    System.err.println(
                            "Mockito is currently self-attaching to enable the inline-mock-maker. This "
                                    + "will no longer work in future releases of the JDK. Please add Mockito as an agent to your "
                                    + "build as described in Mockito's documentation: "
                                    + "https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3");
                }
            }
            // Attempt to dynamically attach, as a last resort.
            instrumentation = installAgent();
        }
        if (!instrumentation.isRetransformClassesSupported()) {
            throw new IllegalStateException(
                    join(
                            "Mockito requires retransformation for creating inline mocks. This feature is unavailable on the current VM.",
                            "",
                            "You cannot use this mock maker on this VM"));
        }
        inst = instrumentation;
        return instrumentation;
    }

    /**
     * Installs the Byte Buddy agent, capturing the diagnostics that Byte Buddy writes when an
     * external self-attach fails.
     *
     * <p>When self-attachment requires an external process, Byte Buddy reports the failure as a
     * bare {@code IllegalStateException} and only writes the actual cause (for example an
     * {@code AttachNotSupportedException} explaining that the VM does not support the attach
     * mechanism) to the file named by the {@code net.bytebuddy.agent.attacher.dump} system
     * property. That property has to be set before the failing attach starts, so users only learn
     * about it after the fact, from the exception's message.
     *
     * <p>This method therefore sets the property to a temporary file for the duration of the
     * attach attempt, and attaches the dumped diagnostics to the thrown exception. If the user
     * configured the property, it is left untouched and its file is read instead.
     */
    private static Instrumentation installAgent() {
        // Literal rather than Attacher.DUMP_PROPERTY: the constant is inlined by the compiler
        // anyway, and referencing the class would require a newer Byte Buddy Agent at runtime
        // than Mockito otherwise supports.
        String dumpProperty = "net.bytebuddy.agent.attacher.dump";
        // The property is process-wide, so serialize the attempts that install it temporarily.
        synchronized (PremainAttachAccess.class) {
            String configuredDump = System.getProperty(dumpProperty);
            File dumpFile = null;
            long existingDumpLength = 0;
            try {
                if (configuredDump == null) {
                    try {
                        dumpFile = File.createTempFile("mockito-attach", ".log");
                        dumpFile.deleteOnExit();
                        System.setProperty(dumpProperty, dumpFile.getAbsolutePath());
                    } catch (IOException | SecurityException ignored) {
                        // Without a dump file, the original exception is still reported.
                        dumpFile = null;
                    }
                } else {
                    // Byte Buddy appends to an existing file, so only read what this attempt adds.
                    existingDumpLength = lengthOf(asFile(configuredDump));
                }
                return ByteBuddyAgent.install();
            } catch (RuntimeException | LinkageError e) {
                File diagnostics = dumpFile != null ? dumpFile : asFile(configuredDump);
                String dump = readDump(diagnostics, existingDumpLength);
                if (dump != null) {
                    e.addSuppressed(
                            new IllegalStateException(
                                    "The Byte Buddy agent could not be attached. The attach process reported:\n"
                                            + dump));
                }
                throw e;
            } finally {
                if (dumpFile != null) {
                    System.clearProperty(dumpProperty);
                    if (!dumpFile.delete()) {
                        dumpFile.deleteOnExit();
                    }
                }
            }
        }
    }

    private static File asFile(String path) {
        return path == null || path.isEmpty() ? null : new File(path);
    }

    private static long lengthOf(File file) {
        return file != null && file.isFile() ? file.length() : 0;
    }

    private static String readDump(File dumpFile, long skip) {
        if (dumpFile == null || !dumpFile.isFile()) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(dumpFile.toPath());
            int offset = (int) Math.min(skip, bytes.length);
            String dump = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
            return dump.trim().isEmpty() ? null : dump.trim();
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    private static Instrumentation doGetInstrumentation(
            Class<?> type, String method, ClassLoader systemClassLoader) {
        try {
            return (Instrumentation)
                    Class.forName(type.getName(), true, systemClassLoader)
                            .getMethod(method)
                            .invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }
}
