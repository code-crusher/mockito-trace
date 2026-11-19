/*
 * Copyright (c) 2007 Mockito contributors
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
 * This annotation is the counterpart of {@link CheckReturnValue}. It marks methods, or all
 * methods of a type, whose return value may safely be ignored. This is typically the case for
 * fluent APIs that return {@code this}, where calling the method for its side effect is a
 * legitimate use.
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
