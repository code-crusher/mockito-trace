/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitoinline;

import org.mockito.Mockito;

/**
 * Helper program for {@link AttachFailureDiagnosticsTest}.
 *
 * <p>It is started in a separate JVM with the attach mechanism disabled, so that the inline mock
 * maker has to fall back to self-attaching and fails. The exception chain, including any
 * suppressed diagnostics, is printed to standard out for the test to inspect.
 */
public class AttachFailureProbe {

    public static void main(String[] args) {
        try {
            Mockito.mock(Runnable.class);
            System.out.println("UNEXPECTED_SUCCESS");
        } catch (Throwable throwable) {
            printThrowable("THROWN", throwable);
        }
    }

    private static void printThrowable(String prefix, Throwable throwable) {
        System.out.println(
                prefix + ": " + throwable.getClass().getName() + ": " + throwable.getMessage());
        for (Throwable suppressed : throwable.getSuppressed()) {
            printThrowable("SUPPRESSED", suppressed);
        }
        Throwable cause = throwable.getCause();
        if (cause != null) {
            printThrowable("CAUSE", cause);
        }
    }
}
