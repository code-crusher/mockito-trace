/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.verification;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockitoutil.TestBase;

/**
 * A helper method may wrap {@code inOrder.verify(mock)} and return the mock so that the caller's
 * chained invocation completes the verification. The helper can run further verifications in the
 * meantime (for example in a {@code finally} block), which must not invalidate the pending
 * verification.
 *
 * @see <a href="https://github.com/mockito/mockito/issues/3449">Issue #3449</a>
 */
public class VerificationWrapperMethodTest extends TestBase {

    public interface Foo extends AutoCloseable {
        void bar();

        void baz(int arg);

        void discard();
    }

    public interface FooFactory {
        Foo getFoo();
    }

    @Mock private Foo foo;
    @Mock private FooFactory fooFactory;

    /**
     * Verifies that the caller's invocation happens while the {@link Foo} is open. The invocation
     * itself is supplied by the caller by chaining a method call onto the returned mock.
     */
    protected Foo verifyFooWhileOpen() throws Exception {
        InOrder inOrder = inOrder(fooFactory, foo);
        inOrder.verify(fooFactory).getFoo();
        try {
            // The caller's invocation completes this verification.
            return inOrder.verify(foo);
        } finally {
            inOrder.verify(foo).close();
            verify(foo, never()).discard();
        }
    }

    @Test
    public void should_complete_verification_with_the_callers_invocation() throws Throwable {
        when(fooFactory.getFoo()).thenReturn(foo);
        try (Foo f = fooFactory.getFoo()) {
            f.bar();
            f.baz(1);
        }

        verifyFooWhileOpen().bar();
        verifyFooWhileOpen().baz(anyInt());
    }

    @Test
    public void should_complete_repeated_wrapped_verifications() throws Throwable {
        when(fooFactory.getFoo()).thenReturn(foo);
        try (Foo f = fooFactory.getFoo()) {
            f.bar();
            f.baz(1);
            f.bar();
            f.baz(2);
        }

        verifyFooWhileOpen().bar();
        verifyFooWhileOpen().baz(1);
        verifyFooWhileOpen().bar();
        verifyFooWhileOpen().baz(2);
    }
}
