/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.creation.bytebuddy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class MockMakerErrorUtilsTest {

    @Test
    public void finds_missing_class_from_NoClassDefFoundError() {
        NoClassDefFoundError error = new NoClassDefFoundError("javax/servlet/http/Part");
        assertThat(MockMakerErrorUtils.findMissingClass(error))
                .isEqualTo("javax.servlet.http.Part");
    }

    @Test
    public void finds_missing_class_from_ClassNotFoundException() {
        ClassNotFoundException exception = new ClassNotFoundException("foo.Missing");
        assertThat(MockMakerErrorUtils.findMissingClass(exception)).isEqualTo("foo.Missing");
    }

    @Test
    public void finds_missing_class_nested_in_cause_chain() {
        NoClassDefFoundError root = new NoClassDefFoundError("foo/Missing");
        IllegalArgumentException wrapped =
                new IllegalArgumentException("Could not create type", root);
        assertThat(MockMakerErrorUtils.findMissingClass(wrapped)).isEqualTo("foo.Missing");
    }

    @Test
    public void ignores_failed_static_initializer() {
        NoClassDefFoundError error = new NoClassDefFoundError("Could not initialize class Foo");
        assertThat(MockMakerErrorUtils.findMissingClass(error)).isNull();
    }

    @Test
    public void ignores_message_that_is_not_a_class_name() {
        NoClassDefFoundError error =
                new NoClassDefFoundError(
                        "Simulate missing transitive dependency used in class init.");
        assertThat(MockMakerErrorUtils.findMissingClass(error)).isNull();
    }

    @Test
    public void returns_null_when_no_missing_class_cause() {
        IllegalArgumentException error = new IllegalArgumentException("Could not create type");
        assertThat(MockMakerErrorUtils.findMissingClass(error)).isNull();
    }
}
