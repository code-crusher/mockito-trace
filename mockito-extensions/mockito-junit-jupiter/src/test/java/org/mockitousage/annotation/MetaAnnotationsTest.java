/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.annotation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies that Mockito's annotations can be used as meta-annotations, also in the
 * JUnit Jupiter extension: fields annotated with a custom annotation meta-annotated with
 * {@link Mock @Mock} or {@link Captor @Captor} are initialized, as are parameters of tests and
 * constructors annotated with such custom annotations.
 */
@ExtendWith(MockitoExtension.class)
class MetaAnnotationsTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
    @Mock
    @interface CdiMock {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
    @Captor
    @interface CdiCaptor {}

    @CdiMock private List<String> mockField;

    @CdiCaptor private ArgumentCaptor<String> captorField;

    @Test
    void initializes_meta_annotated_field() {
        assertNotNull(mockField);
        assertTrue(Mockito.mockingDetails(mockField).isMock());
    }

    @Test
    void initializes_meta_annotated_captor_field() {
        assertNotNull(captorField);
    }

    @Test
    void initializes_meta_annotated_parameter(
            @CdiMock List<String> parameterMock,
            @CdiCaptor ArgumentCaptor<String> parameterCaptor) {
        assertNotNull(parameterMock);
        assertTrue(Mockito.mockingDetails(parameterMock).isMock());
        assertNotNull(parameterCaptor);
    }
}
