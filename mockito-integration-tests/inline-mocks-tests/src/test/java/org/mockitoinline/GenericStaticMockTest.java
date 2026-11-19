/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitoinline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.MockedStatic;

/**
 * Characterizes stubbing of generic static methods with {@code mockStatic}, as reported in issue
 * #3770. A type parameter cannot be named inside an {@code Answer} because it is erased at
 * runtime, but the invocation exposes the type token when the stubbed method receives one.
 */
public class GenericStaticMockTest {

    @Test
    public void answer_can_forward_the_type_token_to_another_generic_method() {
        try (MockedStatic<Bar> mockedBar = mockStatic(Bar.class)) {
            mockedBar
                    .when(() -> Bar.foo(any()))
                    .thenAnswer(invocation -> otherGenericMethod(invocation.getArgument(0)));

            List<String> result = Bar.foo(String.class);

            assertThat(result).isEmpty();
        }
    }

    @Test
    public void answer_can_read_the_type_token_with_the_typed_get_argument_overload() {
        try (MockedStatic<Bar> mockedBar = mockStatic(Bar.class)) {
            mockedBar
                    .when(() -> Bar.foo(any()))
                    .thenAnswer(
                            invocation ->
                                    otherGenericMethod(invocation.getArgument(0, Class.class)));

            List<String> result = Bar.foo(String.class);

            assertThat(result).isEmpty();
        }
    }

    @Test
    public void answer_can_use_the_type_token_held_in_a_local_variable() {
        try (MockedStatic<Bar> mockedBar = mockStatic(Bar.class)) {
            mockedBar
                    .when(() -> Bar.foo(any()))
                    .thenAnswer(
                            invocation -> {
                                Class<?> type = invocation.getArgument(0);
                                List<?> list = otherGenericMethod(type);
                                return list;
                            });

            List<String> result = Bar.foo(String.class);

            assertThat(result).isEmpty();
        }
    }

    @Test
    public void stubbing_a_generic_static_method_returns_the_stubbed_value() {
        try (MockedStatic<Bar> mockedBar = mockStatic(Bar.class)) {
            List<String> expected = new ArrayList<>();
            mockedBar.when(() -> Bar.foo(String.class)).thenReturn(expected);

            assertThat(Bar.foo(String.class)).isSameAs(expected);
        }
    }

    private static <T> List<T> otherGenericMethod(Class<T> type) {
        return new ArrayList<>();
    }

    static class Bar {

        // Mirrors the generic method from the issue. Its body never runs in these tests because
        // the static mock replaces it.
        static <T> List<T> foo(Class<T> type) {
            List<T> list = new ArrayList<>();
            list = foo(type);
            return list;
        }
    }
}
