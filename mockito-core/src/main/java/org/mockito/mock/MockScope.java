/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.mock;

/**
 * The scope in which a mock is active.
 *
 * @see org.mockito.MockSettings#scope(MockScope)
 * @since 5.25.0
 */
public enum MockScope {

    /**
     * The mock is only active on the thread that created it (the default). Other threads are not
     * affected, which keeps concurrently running tests from interfering with one another.
     */
    THREAD,

    /**
     * The mock is active on all threads until it is closed. This is convenient when the code under
     * test delegates to worker threads, for example when tasks are submitted to an
     * {@link java.util.concurrent.ExecutorService}. Note that a global mock interferes with any
     * other code that is running concurrently in the same JVM, so it should be released as soon as
     * possible, for example by using a try-with-resources block.
     */
    GLOBAL
}
