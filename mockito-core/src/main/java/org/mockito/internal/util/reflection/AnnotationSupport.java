/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.util.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utilities to work with annotations, including <em>meta-annotations</em>, i.e. annotations that are
 * themselves annotated with other annotations. This allows defining custom annotations that are
 * meta-annotated with Mockito annotations such as {@link org.mockito.Mock @Mock}, much like JUnit
 * Jupiter's meta-annotations.
 *
 * <p>Only annotations with {@link java.lang.annotation.RetentionPolicy#RUNTIME retention} are visible
 * at runtime and are therefore considered by these methods.
 *
 * <p>Annotations are discovered breadth-first starting from the element itself, so annotations applied
 * directly to the element are reported before their meta-annotations. Identical annotations (equal by
 * value) are reported only once; if the same annotation type occurs with different values through
 * different paths, each distinct occurrence is reported once. The annotations in
 * {@code java.lang.annotation} (such as {@code @Retention} and {@code @Target}) are never reported.
 */
public final class AnnotationSupport {

    private AnnotationSupport() {}

    /**
     * Determine if the given element or one of its annotations (recursively) is annotated with the
     * given annotation type.
     *
     * @param element the element to inspect
     * @param annotationType the annotation type to look for
     * @return {@code true} if the annotation type is present on the element or on any (meta-)annotation
     *     present on the element, directly or indirectly
     */
    public static boolean isAnnotated(
            AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return findAnnotation(element, annotationType) != null;
    }

    /**
     * Find the annotation of the given type that is present on the given element, either directly or
     * through meta-annotations.
     *
     * <p>If multiple distinct annotations of the given type are present, the one occurring first in
     * breadth-first order (direct annotations first, before their meta-annotations) is returned.
     *
     * @param element the element to inspect
     * @param annotationType the annotation type to look for
     * @param <A> the annotation type
     * @return the found annotation, or {@code null} if not present
     */
    public static <A extends Annotation> A findAnnotation(
            AnnotatedElement element, Class<A> annotationType) {
        List<A> found = findAnnotations(element, annotationType);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * Find all annotations of the given type that are present on the given element, either directly or
     * through meta-annotations.
     *
     * @param element the element to inspect
     * @param annotationType the annotation type to look for
     * @param <A> the annotation type
     * @return the found annotations, never {@code null}; empty if not present
     */
    public static <A extends Annotation> List<A> findAnnotations(
            AnnotatedElement element, Class<A> annotationType) {
        List<A> result = new ArrayList<>();
        for (Annotation annotation : findAll(element)) {
            if (annotationType.isInstance(annotation)) {
                result.add(annotationType.cast(annotation));
            }
        }
        return result;
    }

    /**
     * Find all annotations present on the given element, including those found recursively on the
     * annotations themselves (meta-annotations).
     *
     * <p>The returned annotations are ordered breadth-first: annotations applied to the element in
     * declaration order first, then their meta-annotations level by level. Identical annotations
     * (equal by value) are reported only once.
     *
     * @param element the element to inspect
     * @return the annotations found, never {@code null}
     */
    public static List<Annotation> findAll(AnnotatedElement element) {
        List<Annotation> result = new ArrayList<>();
        Set<Annotation> seen = new HashSet<>();
        Set<Class<? extends Annotation>> expanded = new HashSet<>();
        Deque<AnnotatedElement> queue = new ArrayDeque<>();
        queue.add(element);
        while (!queue.isEmpty()) {
            for (Annotation annotation : queue.poll().getAnnotations()) {
                Class<? extends Annotation> type = annotation.annotationType();
                if (type.getName().startsWith("java.lang.annotation.")) {
                    // skip @Retention, @Target, @Documented, @Inherited, etc.
                    continue;
                }
                if (seen.add(annotation)) {
                    result.add(annotation);
                }
                if (expanded.add(type)) {
                    queue.add(type);
                }
            }
        }
        return result;
    }
}
