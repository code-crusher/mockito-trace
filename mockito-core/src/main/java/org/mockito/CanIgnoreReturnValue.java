/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is not supposed to be used by Mockito end-users. Instead, we
 * use it to annotate methods for Static Analysis tools, including FindBugs and ErrorProne.
 * These tools can check whether the return value of our Mockito methods are actually
 * used. As such, Mockito State Validation can be performed at compile-time rather than run-time.
 * <p>
 * This annotation indicates that the return value of the annotated method (or of any
 * method of the annotated type) may safely be ignored, exempting it from the
 * {@link CheckReturnValue} check.
 * <p>
 * This annotation is public, because we have to use it in multiple packages.
 *
 * @see CheckReturnValue
 * @see <a href="https://errorprone.info/bugpattern/CheckReturnValue">ErrorProne check</a>
 * @since 5.25.0
 */
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface CanIgnoreReturnValue {}
