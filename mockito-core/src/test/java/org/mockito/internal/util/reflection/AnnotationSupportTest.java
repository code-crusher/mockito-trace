/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.util.reflection;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;

public class AnnotationSupportTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @interface Meta {
        String value() default "meta";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @interface NestedMeta {
        String value() default "nested";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Meta
    @NestedMeta
    @interface MetaAnnotated {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @MetaAnnotated
    @interface DoubleMetaAnnotated {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Meta("left")
    @interface Left {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Meta("right")
    @interface Right {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Left
    @Right
    @interface TwoPaths {}

    @SuppressWarnings("unused")
    private static class Fields {
        @Meta("direct")
        String direct;

        @MetaAnnotated String metaAnnotated;

        @DoubleMetaAnnotated String doubleMetaAnnotated;

        @TwoPaths String twoPaths;

        String unannotated;
    }

    @Test
    public void should_find_direct_annotation() throws Exception {
        Field field = Fields.class.getDeclaredField("direct");

        assertThat(AnnotationSupport.isAnnotated(field, Meta.class)).isTrue();
        assertThat(AnnotationSupport.findAnnotation(field, Meta.class).value()).isEqualTo("direct");
        assertThat(AnnotationSupport.findAnnotations(field, Meta.class)).hasSize(1);
    }

    @Test
    public void should_find_meta_annotations() throws Exception {
        Field field = Fields.class.getDeclaredField("metaAnnotated");

        assertThat(AnnotationSupport.isAnnotated(field, Meta.class)).isTrue();
        assertThat(AnnotationSupport.isAnnotated(field, NestedMeta.class)).isTrue();
        assertThat(AnnotationSupport.isAnnotated(field, MetaAnnotated.class)).isTrue();
        assertThat(AnnotationSupport.findAnnotation(field, NestedMeta.class).value())
                .isEqualTo("nested");
    }

    @Test
    public void should_find_transitive_meta_annotations() throws Exception {
        Field field = Fields.class.getDeclaredField("doubleMetaAnnotated");

        assertThat(AnnotationSupport.isAnnotated(field, DoubleMetaAnnotated.class)).isTrue();
        assertThat(AnnotationSupport.isAnnotated(field, MetaAnnotated.class)).isTrue();
        assertThat(AnnotationSupport.isAnnotated(field, Meta.class)).isTrue();
        assertThat(AnnotationSupport.isAnnotated(field, NestedMeta.class)).isTrue();
        assertThat(AnnotationSupport.findAll(field))
                .extracting(Annotation::annotationType)
                .containsExactly(
                        DoubleMetaAnnotated.class,
                        MetaAnnotated.class,
                        Meta.class,
                        NestedMeta.class);
    }

    @Test
    public void should_prefer_direct_annotations_over_meta_annotations() throws Exception {
        Field field = Fields.class.getDeclaredField("direct");

        // no meta-annotation present, but direct match must win anyway
        assertThat(AnnotationSupport.findAnnotation(field, Meta.class).value()).isEqualTo("direct");
    }

    @Test
    public void
            should_report_first_breadth_first_match_when_multiple_paths_lead_to_same_annotation()
                    throws Exception {
        Field field = Fields.class.getDeclaredField("twoPaths");

        // both @Left and @Right carry a @Meta; breadth-first order prefers the first declared one
        assertThat(AnnotationSupport.findAnnotation(field, Meta.class).value()).isEqualTo("left");
        assertThat(AnnotationSupport.findAnnotations(field, Meta.class))
                .extracting(Meta::value)
                .containsExactly("left", "right");
    }

    @Test
    public void should_report_identical_annotations_only_once() throws Exception {
        Field field = Fields.class.getDeclaredField("twoPaths");

        assertThat(AnnotationSupport.findAnnotations(field, Left.class)).hasSize(1);
    }

    @Test
    public void should_return_empty_when_annotation_absent() throws Exception {
        Field field = Fields.class.getDeclaredField("unannotated");

        assertThat(AnnotationSupport.isAnnotated(field, Meta.class)).isFalse();
        assertThat(AnnotationSupport.findAnnotation(field, Meta.class)).isNull();
        assertThat(AnnotationSupport.findAnnotations(field, Meta.class)).isEmpty();
        assertThat(AnnotationSupport.findAll(field)).isEmpty();
    }

    @Test
    public void should_work_on_methods() throws Exception {
        Method method = getClass().getDeclaredMethod("annotatedMethod");

        assertThat(AnnotationSupport.isAnnotated(method, Meta.class)).isTrue();
        assertThat(AnnotationSupport.findAnnotation(method, Meta.class).value()).isEqualTo("meta");
    }

    @MetaAnnotated
    private void annotatedMethod() {}
}
