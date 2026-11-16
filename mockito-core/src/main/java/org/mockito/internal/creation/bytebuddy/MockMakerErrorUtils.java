/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.creation.bytebuddy;

/**
 * Helpers to diagnose mock creation failures that are caused by incomplete classpaths rather than
 * by the mocked type itself.
 *
 * @see SubclassByteBuddyMockMaker
 * @see InlineDelegateByteBuddyMockMaker
 */
final class MockMakerErrorUtils {

    private static final String FAILED_CLINIT_MESSAGE = "Could not initialize class";

    private MockMakerErrorUtils() {}

    /**
     * Returns the name of the first class that could not be found while creating the mock, or
     * {@code null} if the failure is not caused by a missing classpath entry.
     *
     * <p>When a mocked type references a class that is missing from the classpath (for example a
     * method signature referencing a type provided by another artifact), the bytecode generator
     * fails with a {@link NoClassDefFoundError} wrapped inside the generation error. Such
     * failures are dependency problems, not limitations of the mocked type, and deserve a
     * dedicated error message pointing the user at the missing class.
     *
     * @param generationFailed the error thrown while generating the mock's bytecode
     * @return the missing class name or {@code null} if no class is missing
     */
    static String findMissingClass(Throwable generationFailed) {
        for (Throwable cause = generationFailed;
                cause != null;
                cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof NoClassDefFoundError || cause instanceof ClassNotFoundException) {
                String message = cause.getMessage();
                if (message != null
                        && !message.startsWith(FAILED_CLINIT_MESSAGE)
                        && isClassName(message)) {
                    return message.replace('/', '.');
                }
                return null;
            }
        }
        return null;
    }

    /**
     * Heuristically checks whether the message of a {@link NoClassDefFoundError} looks like a
     * binary class name. Messages like <code>Could not initialize class Foo</code> indicate a
     * failed static initializer rather than a missing classpath entry.
     */
    private static boolean isClassName(String message) {
        for (String part : message.split("[./]")) {
            if (part.isEmpty() || !Character.isJavaIdentifierStart(part.charAt(0))) {
                return false;
            }
            for (int i = 1; i < part.length(); i++) {
                if (!Character.isJavaIdentifierPart(part.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }
}
