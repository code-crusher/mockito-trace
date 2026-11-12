/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.stubbing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Map;

import org.junit.Test;
import org.mockito.invocation.Invocation;
import org.mockito.invocation.InvocationOnMock;

public class InvocationOnMockInvocationCountTest {

    @Test
    public void should_count_one_based_including_the_current_invocation() {
        Map<String, Integer> mock = mock();
        whenGetFoo(mock);

        assertThat(mock.get("foo")).isEqualTo(1);
        assertThat(mock.get("foo")).isEqualTo(2);
        assertThat(mock.get("foo")).isEqualTo(3);
    }

    @Test
    public void should_support_failing_a_number_of_times_then_returning_a_value() {
        Map<String, Integer> mock = mock();
        when(mock.get("foo"))
                .thenAnswer(
                        invocation -> {
                            if (invocation.getInvocationCount() <= 2) {
                                throw new RuntimeException("Oupps");
                            }
                            return 200;
                        });

        assertThatThrownBy(() -> mock.get("foo")).hasMessage("Oupps");
        assertThatThrownBy(() -> mock.get("foo")).hasMessage("Oupps");
        assertThat(mock.get("foo")).isEqualTo(200);
    }

    @Test
    public void should_count_each_stubbing_independently() {
        Map<String, Integer> mock = mock();
        whenGetFoo(mock);
        when(mock.get("bar")).thenAnswer(InvocationOnMock::getInvocationCount);

        mock.get("foo");
        mock.get("foo");
        mock.get("bar");

        assertThat(mock.get("foo")).isEqualTo(3);
        assertThat(mock.get("bar")).isEqualTo(2);
    }

    @Test
    public void should_count_each_method_independently() {
        Map<String, Integer> mock = mock();
        whenGetFoo(mock);
        when(mock.put("bar", 2)).thenAnswer(InvocationOnMock::getInvocationCount);

        mock.get("foo");
        mock.put("bar", 2);
        mock.put("bar", 2);
        mock.put("bar", 2);

        assertThat(mock.get("foo")).isEqualTo(2);
        assertThat(mock.put("bar", 2)).isEqualTo(4);
    }

    @Test
    public void should_count_each_mock_independently() {
        Map<String, Integer> firstMock = mock();
        Map<String, Integer> secondMock = mock();
        whenGetFoo(firstMock);
        whenGetFoo(secondMock);

        firstMock.get("foo");
        firstMock.get("foo");

        assertThat(firstMock.get("foo")).isEqualTo(3);
        assertThat(secondMock.get("foo")).isEqualTo(1);
    }

    @Test
    public void should_support_null_arguments() {
        Map<String, Integer> mock = mock();
        when(mock.get(null)).thenAnswer(InvocationOnMock::getInvocationCount);

        assertThat(mock.get(null)).isEqualTo(1);
        assertThat(mock.get(null)).isEqualTo(2);
    }

    @Test
    public void should_support_varargs() {
        VarArgs mock = mock();
        when(mock.join("a", "b")).thenAnswer(InvocationOnMock::getInvocationCount);

        assertThat(mock.join("a", "b")).isEqualTo(1);
        assertThat(mock.join("a", "b")).isEqualTo(2);
    }

    @Test
    public void should_follow_matchers_rather_than_argument_equality() {
        Map<String, Integer> mock = mock();
        when(mock.get(anyString())).thenAnswer(InvocationOnMock::getInvocationCount);

        assertThat(mock.get("a")).isEqualTo(1);
        assertThat(mock.get("b")).isEqualTo(2);
        assertThat(mock.get("a")).isEqualTo(3);
    }

    @Test
    public void should_share_one_counter_across_consecutive_answers() {
        Map<String, Integer> mock = mock();
        when(mock.get("foo"))
                .thenAnswer(InvocationOnMock::getInvocationCount)
                .thenAnswer(invocation -> invocation.getInvocationCount() * 10);

        assertThat(mock.get("foo")).isEqualTo(1);
        assertThat(mock.get("foo")).isEqualTo(20);
        assertThat(mock.get("foo")).isEqualTo(30);
    }

    @Test
    public void should_count_on_stub_only_mocks() {
        Map<String, Integer> mock = mock(withSettings().stubOnly());
        whenGetFoo(mock);

        assertThat(mock.get("foo")).isEqualTo(1);
        assertThat(mock.get("foo")).isEqualTo(2);
    }

    @Test
    public void should_count_for_answer_first_stubbing() {
        Map<String, Integer> mock = mock();
        doAnswer(InvocationOnMock::getInvocationCount).when(mock).get("foo");

        assertThat(mock.get("foo")).isEqualTo(1);
        assertThat(mock.get("foo")).isEqualTo(2);
    }

    @Test
    public void should_return_zero_for_invocations_not_matched_by_any_stubbing() {
        Map<String, Integer> mock = mock();
        whenGetFoo(mock);

        int size = mock.size();
        assertThat(size).isZero();
        assertThat(mock.get("foo")).isEqualTo(1);

        assertThat(mockingDetails(mock).getInvocations())
                .extracting(Invocation::getInvocationCount)
                .containsExactly(0, 1);
    }

    @Test
    public void should_not_reset_count_when_clearing_invocations() {
        Map<String, Integer> mock = mock();
        whenGetFoo(mock);

        assertThat(mock.get("foo")).isEqualTo(1);
        assertThat(mock.get("foo")).isEqualTo(2);
        clearInvocations(mock);
        assertThat(mock.get("foo")).isEqualTo(3);
        assertThat(mock.get("foo")).isEqualTo(4);
    }

    private static void whenGetFoo(Map<String, Integer> mock) {
        when(mock.get("foo")).thenAnswer(InvocationOnMock::getInvocationCount);
    }

    private interface VarArgs {
        int join(String first, String... rest);
    }
}
