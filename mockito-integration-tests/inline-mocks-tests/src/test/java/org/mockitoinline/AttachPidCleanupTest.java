/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitoinline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;

/**
 * Regression test for <a href="https://github.com/mockito/mockito/issues/3431">issue #3431</a>:
 * stale {@code .attach_pid<pid>} files left behind in the working directory when the target JVM
 * of a dynamic attach dies before the attachment completes.
 *
 * <p>The attach handshake creates {@code .attach_pid<pid>} in the target's working directory
 * (resolved via {@code /proc/<pid>/cwd/}) and deletes it afterwards. When the target process
 * dies mid-attach, the {@code /proc} entry disappears and the cleanup used to fail silently,
 * leaving an empty {@code .attach_pid<pid>} file behind. Byte Buddy resolves the file to its
 * canonical path before cleanup (byte-buddy 1.18.5+, see byte-buddy #1883/#1884), so the file
 * can be removed even after the target process is gone.
 *
 * <p>This test drives the same code path Mockito uses for self-attachment
 * ({@code ByteBuddyAgent.install()} resolves to Byte Buddy's {@code VirtualMachine}
 * implementation) by spawning a helper JVM that starts a dynamic attach to a target JVM and
 * kills that target mid-attach. The helper exits non-zero if a stale {@code .attach_pid} file
 * remains in the target's working directory.
 */
public class AttachPidCleanupTest {

    /**
     * Name of the helper class that runs the attach-race inside a separate JVM. This class is
     * spawned as an external process so that killing the target JVM cannot take down the JUnit
     * runtime and so that we control the target's working directory precisely.
     */
    private static final String HELPER_CLASS = AttachRaceHelper.class.getName();

    @Test
    public void no_attach_pid_file_is_left_behind_when_target_dies_mid_attach() throws Exception {
        Assume.assumeTrue("Attach handshake via /proc is Linux-specific", isLinux());
        Assume.assumeTrue("The attach mechanism is HotSpot-specific", isHotSpot());

        Path workingDirectory = Files.createTempDirectory("attach-pid-cleanup");
        try {
            String javaHome = System.getProperty("java.home");
            String java = javaHome + File.separator + "bin" + File.separator + "java";

            String classpath = System.getProperty("java.class.path");
            ProcessBuilder builder =
                    new ProcessBuilder(
                            java, "-cp", classpath, HELPER_CLASS, workingDirectory.toString());
            builder.redirectErrorStream(true);
            Process helper = builder.start();

            String output = new String(helper.getInputStream().readAllBytes()).trim();
            int exitCode = helper.waitFor();

            assertThat(output)
                    .as(
                            "Helper JVM output (exit code %d). A stale .attach_pid file was left in %s",
                            exitCode, workingDirectory)
                    .contains("PASS");
            assertThat(exitCode)
                    .as("Helper JVM should exit with code 0, output:%n%s", output)
                    .isZero();

            List<Path> staleFiles = findAttachPidFiles(workingDirectory);
            assertThat(staleFiles)
                    .as("No stale .attach_pid files should remain in %s", workingDirectory)
                    .isEmpty();
        } finally {
            deleteRecursively(workingDirectory.toFile());
        }
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private static boolean isHotSpot() {
        String vmName = System.getProperty("java.vm.name", "").toLowerCase();
        return vmName.contains("hotspot") || vmName.contains("openjdk");
    }

    private static List<Path> findAttachPidFiles(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(path -> path.getFileName().toString().startsWith(".attach_pid"))
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        // Stale attach files can only be removed if the test succeeds, ignore failures here.
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
