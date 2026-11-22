/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitoinline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.concurrent.TimeUnit;
import org.junit.Assume;
import org.junit.Test;

/**
 * Regression test for <a href="https://github.com/mockito/mockito/issues/3716">issue #3716</a>:
 * when the inline mock maker cannot self-attach, Mockito used to report only that the JDK
 * "does not supply a working agent attachment mechanism", while the actual cause was hidden in a
 * Byte Buddy dump file that users had to know about and configure in advance.
 *
 * <p>This test starts a separate JVM with the attach mechanism disabled, which makes Mockito's
 * self-attach fail exactly as reported in the issue. It then asserts that the failure now carries
 * the underlying attach error, so users can tell why the attach failed.
 */
public class AttachFailureDiagnosticsTest {

    @Test
    public void self_attach_failure_reports_the_underlying_attach_error() throws Exception {
        Assume.assumeTrue("Disabling the attach mechanism is a HotSpot feature", isHotSpot());

        String java =
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder builder =
                new ProcessBuilder(
                        java,
                        "-XX:+DisableAttachMechanism",
                        "-cp",
                        System.getProperty("java.class.path"),
                        AttachFailureProbe.class.getName());
        builder.redirectErrorStream(true);
        Process probe = builder.start();
        boolean finished = probe.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            probe.destroyForcibly();
        }
        String output = new String(probe.getInputStream().readAllBytes());

        assertThat(finished).as("Probe JVM should terminate, output:%n%s", output).isTrue();
        assertThat(output).doesNotContain("UNEXPECTED_SUCCESS");
        assertThat(output)
                .as("The self-attach failure should report the underlying attach error")
                .contains("The Byte Buddy agent could not be attached");
        assertThat(output)
                .as("The reported attach error should name the attach mechanism")
                .contains("does not support the attach mechanism");
    }

    private static boolean isHotSpot() {
        String vmName = System.getProperty("java.vm.name", "").toLowerCase();
        return vmName.contains("hotspot") || vmName.contains("openjdk");
    }
}
