/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitoinline;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Helper JVM for {@link AttachPidCleanupTest}. Not a JUnit test.
 *
 * <p>Spawns a <em>target</em> JVM in the given working directory and starts a dynamic attach
 * against it using Byte Buddy's emulated HotSpot attach (the code Mockito's self-attachment
 * falls back to when the JDK attach API is unavailable). The target JVM is killed while the
 * attach handshake is still polling for the response socket, i.e. exactly the situation that
 * used to leave a stale {@code .attach_pid<pid>} file behind: the {@code /proc/<pid>/cwd}
 * link disappears with the dying process, so deleting the attach file through the unresolved
 * {@code /proc} path fails silently (see <a href="https://github.com/mockito/mockito/issues/3431">issue
 * #3431</a>). Byte Buddy resolves the attach file to its canonical path before cleanup, so the
 * file is removed even after the target process is gone.
 *
 * <p>Exits with code 0 and prints {@code PASS} if no {@code .attach_pid} file remains in the
 * target's working directory, otherwise prints {@code FAIL} and exits with code 1.
 */
public class AttachRaceHelper {

    private static final String TARGET_MAIN = SleepingTarget.class.getName();

    public static void main(String[] args) throws Exception {
        File workingDirectory = new File(args[0]);

        Process target = spawnTargetJvm(workingDirectory);
        long targetPid = target.pid();

        Thread killer =
                new Thread(
                        () -> {
                            try {
                                // Byte Buddy's connect() polls for the response socket with a
                                // fixed number of attempts (we configure 30 x 100ms below). Kill
                                // the target while the attacher is still polling, so the attach
                                // file cleanup runs after /proc/<pid>/cwd has already vanished.
                                TimeUnit.MILLISECONDS.sleep(500);
                                target.destroyForcibly();
                            } catch (InterruptedException ignored) {
                            }
                        });
        killer.setDaemon(true);
        killer.start();

        // The attach must fail because the target is killed before it can respond. We only care
        // about the cleanup of the .attach_pid file, not about the attach succeeding.
        try {
            attachTo(String.valueOf(targetPid));
            System.out.println("FAIL: attach unexpectedly succeeded");
            System.exit(1);
        } catch (Exception expected) {
            // Expected: the target VM did not respond in time.
        }
        killer.join();
        target.waitFor();

        boolean staleFileExists = false;
        File[] files = workingDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().startsWith(".attach_pid")) {
                    staleFileExists = true;
                }
            }
        }
        if (staleFileExists) {
            System.out.println(
                    "FAIL: stale .attach_pid" + targetPid + " file left in " + workingDirectory);
            System.exit(1);
        }
        System.out.println("PASS");
        System.exit(0);
    }

    private static Process spawnTargetJvm(File workingDirectory) throws Exception {
        String javaHome = System.getProperty("java.home");
        String java = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        // The target never starts its attach listener, so the attach handshake is still in
        // progress when we kill it.
        ProcessBuilder builder =
                new ProcessBuilder(
                        java, "-XX:+DisableAttachMechanism", "-cp", classpath, TARGET_MAIN);
        builder.directory(workingDirectory);
        Process target = builder.start();
        // Drain the target's output so it cannot block on a full pipe.
        target.getInputStream().close();
        return target;
    }

    /**
     * Drives Byte Buddy's emulated HotSpot attach, i.e. the code that runs when Mockito
     * self-attaches on a VM without the JDK attach API. Reflection is used because these types
     * are internal to Byte Buddy.
     */
    private static void attachTo(String pid) throws Exception {
        Class<?> factoryClass =
                Class.forName(
                        "net.bytebuddy.agent.VirtualMachine$ForHotSpot$Connection$ForJnaPosixSocket$Factory");
        Constructor<?> constructor =
                factoryClass.getDeclaredConstructor(
                        String.class, int.class, long.class, TimeUnit.class);
        constructor.setAccessible(true);
        Object factory = constructor.newInstance("/tmp", 30, 100L, TimeUnit.MILLISECONDS);

        Class<?> factoryInterface =
                Class.forName("net.bytebuddy.agent.VirtualMachine$ForHotSpot$Connection$Factory");
        Method attach =
                Class.forName("net.bytebuddy.agent.VirtualMachine$ForHotSpot")
                        .getMethod("attach", String.class, factoryInterface);
        attach.invoke(null, pid, factory);
    }

    /**
     * The target JVM: it sleeps and, thanks to {@code -XX:+DisableAttachMechanism}, never
     * starts the attach listener.
     */
    public static class SleepingTarget {
        public static void main(String[] args) throws Exception {
            Thread.sleep(60_000);
        }
    }
}
