/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.bugs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockitoutil.TestBase;

/**
 * Characterization test for the exact stubbing shape reported in issue #3549, where stubbing a
 * two-argument method with {@code anyString()} and {@code any(StringBuilder.class)} (an
 * {@link org.mockito.ArgumentMatchers#any(Class)} matcher whose default value for a reference type
 * is {@code null}) was suspected of failing with InvalidUseOfMatchersException on Mockito 5.x
 * while working on Mockito 4.x.
 *
 * <p>The scenario works on both Mockito 4.x and 5.x: the null returned by the {@code any(Class)}
 * matcher is only the dummy argument passed to the mock during stubbing, and once the stubbing is
 * completed the matcher matches real, non-null invocations. The reported failure required an
 * unconsumed matcher left behind by an earlier test or a genuinely misused matcher, both of which
 * fail identically on Mockito 4.x.
 *
 * @see <a href="https://github.com/mockito/mockito/issues/3549">Issue #3549</a>
 */
public class StubbingWithStringBuilderAndTypedAnyMatcherTest extends TestBase {

    interface MockClass {
        Integer getMethod(String value, StringBuilder builder);
    }

    @Mock MockClass mockObject;

    @Test
    public void stubbing_with_anyString_and_typed_any_matcher_binds_and_matches_real_arguments() {
        final RuntimeException toThrow = new RuntimeException("Runtime exception");

        when(mockObject.getMethod(anyString(), any(StringBuilder.class)))
                .then(
                        new Answer<Integer>() {
                            @Override
                            public Integer answer(InvocationOnMock invocation) {
                                StringBuilder builder = invocation.getArgument(1);
                                builder.append("appended");
                                throw toThrow;
                            }
                        });

        StringBuilder builder = new StringBuilder();
        try {
            mockObject.getMethod("x", builder);
            fail("the answer should have thrown");
        } catch (RuntimeException e) {
            // the stubbing matched the real invocation and the answer ran, mutating the argument
            assertSame(toThrow, e);
        }
        assertEquals("appended", builder.toString());
    }

    @Test
    public void stubbing_with_anyString_and_typed_any_matcher_matches_subsequent_calls() {
        when(mockObject.getMethod(anyString(), any(StringBuilder.class))).thenReturn(42);

        assertEquals(
                Integer.valueOf(42), mockObject.getMethod("x", new StringBuilder("real builder")));
        // any(Class) performs a type check and does not match null arguments, so this call
        // falls through to the mock's default answer
        assertEquals(Integer.valueOf(0), mockObject.getMethod("x", null));
    }
}
