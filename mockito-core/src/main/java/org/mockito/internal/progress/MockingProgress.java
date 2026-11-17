/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.progress;

import java.util.Set;

import org.mockito.invocation.Location;

import org.mockito.listeners.MockitoListener;
import org.mockito.listeners.VerificationListener;
import org.mockito.mock.MockCreationSettings;
import org.mockito.stubbing.OngoingStubbing;
import org.mockito.verification.VerificationMode;
import org.mockito.verification.VerificationStrategy;

public interface MockingProgress {

    void reportOngoingStubbing(OngoingStubbing<?> ongoingStubbing);

    OngoingStubbing<?> pullOngoingStubbing();

    Set<VerificationListener> verificationListeners();

    void verificationStarted(VerificationMode verificationMode);

    VerificationMode pullVerificationMode();

    void stubbingStarted(StubbingStyle style);

    void stubbingCompleted();

    void validateState();

    /**
     * Like {@link #validateState()}, but tolerates a pending verification started by
     * {@code InOrder.verify(mock)} that the caller has not completed yet. Such a verification is
     * completed by the invocation chained onto the returned mock, which may only happen after
     * other verifications have run.
     */
    void validateStateAllowingDeferredVerification();

    /**
     * Whether a verification started by {@code InOrder.verify(mock)} is still waiting for the
     * caller's invocation to complete it.
     */
    boolean hasDeferredVerification();

    /**
     * Defers a verification until the pending deferred verification is completed.
     *
     * <p>In-order verification is strictly sequential: verifying an invocation that happened
     * before the one being deferred would fail. Postponing keeps the original ordering.
     */
    void postponeVerification(Runnable verification);

    /** Runs all verifications deferred via {@link #postponeVerification(Runnable)}. */
    void runPostponedVerifications();

    /** Discards verifications deferred via {@link #postponeVerification(Runnable)}. */
    void clearPostponedVerifications();

    boolean isAwaitingStubbingTarget();

    /** Ends target selection while leaving the stubbing unfinished until the method is supplied. */
    void stubbingTargetSelectionCompleted();

    /**
     * Temporarily clears unfinished-stubbing tracking and returns the previous location.
     *
     * <p>Used during target selection so deep-stub invocations can run without triggering
     * unfinished-stubbing validation.
     *
     * @return previous stubbing location, or {@code null} when stubbing was not in progress
     */
    Location suspendStubbingInProgress();

    /**
     * Restores target selection and unfinished-stubbing tracking after
     * {@link #suspendStubbingInProgress()}.
     *
     * @param location value returned by {@link #suspendStubbingInProgress()}, may be {@code null}
     */
    void resumeStubbingInProgress(Location location);

    void reset();

    /**
     * Removes ongoing stubbing so that in case the framework is misused
     * state validation errors are more accurate
     */
    void resetOngoingStubbing();

    ArgumentMatcherStorage getArgumentMatcherStorage();

    void mockingStarted(Object mock, MockCreationSettings<?> settings);

    void mockingStarted(Class<?> mock, MockCreationSettings<?> settings);

    void singletonMockingStarted(Object mock, MockCreationSettings<?> settings);

    void addListener(MockitoListener listener);

    void removeListener(MockitoListener listener);

    void setVerificationStrategy(VerificationStrategy strategy);

    VerificationMode maybeVerifyLazily(VerificationMode mode);

    /**
     * Removes all listeners added via {@link #addListener(MockitoListener)}.
     */
    void clearListeners();
}
