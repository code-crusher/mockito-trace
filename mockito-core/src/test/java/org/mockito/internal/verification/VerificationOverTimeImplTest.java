/*
 * Copyright (c) 2017 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.verification;

import static java.lang.System.currentTimeMillis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.exceptions.base.MockitoAssertionError;
import org.mockito.exceptions.verification.opentest4j.ArgumentsAreDifferent;
import org.mockito.internal.verification.api.VerificationData;
import org.mockito.verification.VerificationMode;

public class VerificationOverTimeImplTest {
    @Mock private VerificationMode delegate;
    private VerificationOverTimeImpl impl;

    @Before
    public void setUp() {
        openMocks(this);
        impl = new VerificationOverTimeImpl(10, 1000, delegate, true);
    }

    @Test
    public void should_return_on_success() {
        impl.verify(null);
        verify(delegate).verify(null);
    }

    @Test
    public void should_throw_mockito_assertion_error() {
        MockitoAssertionError toBeThrown = new MockitoAssertionError("message");

        doThrow(toBeThrown).when(delegate).verify(null);
        assertThatThrownBy(
                        () -> {
                            impl.verify(null);
                        })
                .isEqualTo(toBeThrown);
    }

    @Test
    public void should_deal_with_junit_assertion_error() {
        ArgumentsAreDifferent toBeThrown = new ArgumentsAreDifferent("message", "wanted", "actual");

        doThrow(toBeThrown).when(delegate).verify(null);
        assertThatThrownBy(
                        () -> {
                            impl.verify(null);
                        })
                .isEqualTo(toBeThrown);
    }

    @Test
    public void should_not_wrap_other_exceptions() {
        RuntimeException toBeThrown = new RuntimeException();

        doThrow(toBeThrown).when(delegate).verify(null);
        assertThatThrownBy(
                        () -> {
                            impl.verify(null);
                        })
                .isEqualTo(toBeThrown);
    }

    @Test
    public void should_wait_between_polls_when_delegate_is_satisfied() {
        CountingVerificationMode countingDelegate = new CountingVerificationMode();
        VerificationOverTimeImpl after =
                new VerificationOverTimeImpl(10, 200, countingDelegate, false);

        long start = currentTimeMillis();
        after.verify(null);
        long elapsed = currentTimeMillis() - start;

        // the full duration must pass before the verification succeeds (#3868)
        assertThat(elapsed).isGreaterThanOrEqualTo(200);
        // polling every 10ms instead of busy-spinning pins a core and re-scans every invocation
        // on the mock on each poll, so the delegate must not be invoked more than once per period
        assertThat(countingDelegate.invocationCount.get())
                .isLessThanOrEqualTo((int) (elapsed / 10 + 1));
    }

    private static class CountingVerificationMode implements VerificationMode {

        private final AtomicInteger invocationCount = new AtomicInteger();

        @Override
        public void verify(VerificationData data) {
            invocationCount.incrementAndGet();
        }
    }
}
