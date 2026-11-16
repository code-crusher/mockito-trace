/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.exceptions.base.MockitoException;

/**
 * Verifies that Mockito's field annotations can be used as meta-annotations, i.e. that custom
 * annotations meta-annotated with e.g. {@link Mock @Mock} are honored by
 * {@link MockitoAnnotations#openMocks(Object)}.
 */
public class MetaAnnotationsTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Mock
    @interface CdiMock {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Mock(name = "customMockName", answer = org.mockito.Answers.RETURNS_MOCKS)
    @interface NamedMock {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Spy
    @interface CdiSpy {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
    @Captor
    @interface CdiCaptor {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @InjectMocks
    @interface CdiInjectMocks {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @CdiMock
    @interface TransitivelyMetaMocked {}

    static class Fixture {
        @CdiMock List<String> mock;

        @NamedMock List<String> namedMock;

        @TransitivelyMetaMocked List<String> transitiveMock;

        @CdiSpy final List<String> spy = new LinkedList<>();

        @CdiCaptor ArgumentCaptor<String> captor;

        @CdiMock List<String> injectedIntoHolder;

        @CdiInjectMocks Holder holder;

        static class Holder {
            private List<String> injectedIntoHolder;
            private List<String> mock;

            List<String> getInjectedIntoHolder() {
                return injectedIntoHolder;
            }

            List<String> getMock() {
                return mock;
            }
        }
    }

    @Test
    public void should_create_mock_for_meta_annotated_field() {
        Fixture fixture = new Fixture();

        MockitoAnnotations.openMocks(fixture);

        assertThat(Mockito.mockingDetails(fixture.mock).isMock()).isTrue();
    }

    @Test
    public void should_apply_mock_settings_from_meta_annotation() {
        Fixture fixture = new Fixture();

        MockitoAnnotations.openMocks(fixture);

        assertThat(
                        Mockito.mockingDetails(fixture.namedMock)
                                .getMockCreationSettings()
                                .getMockName()
                                .toString())
                .isEqualTo("customMockName");
        assertThat(fixture.namedMock.iterator()).isNotNull();
    }

    @Test
    public void should_create_mock_for_transitively_meta_annotated_field() {
        Fixture fixture = new Fixture();

        MockitoAnnotations.openMocks(fixture);

        assertThat(Mockito.mockingDetails(fixture.transitiveMock).isMock()).isTrue();
    }

    @Test
    public void should_create_spy_for_meta_annotated_field() {
        Fixture fixture = new Fixture();

        MockitoAnnotations.openMocks(fixture);

        assertThat(Mockito.mockingDetails(fixture.spy).isSpy()).isTrue();
    }

    @Test
    public void should_create_captor_for_meta_annotated_field() {
        Fixture fixture = new Fixture();

        MockitoAnnotations.openMocks(fixture);

        assertThat(fixture.captor).isNotNull();
    }

    @Test
    public void should_inject_mocks_into_meta_annotated_inject_mocks_field() {
        Fixture fixture = new Fixture();

        MockitoAnnotations.openMocks(fixture);

        assertThat(fixture.holder).isNotNull();
        assertThat(fixture.holder.getMock()).isSameAs(fixture.mock);
        assertThat(fixture.holder.getInjectedIntoHolder()).isSameAs(fixture.injectedIntoHolder);
    }

    @Test
    public void meta_annotated_mocks_should_be_injected_into_inject_mocks_fields() {
        FixtureInjecting fixture = new FixtureInjecting();

        MockitoAnnotations.openMocks(fixture);

        assertThat(fixture.holder.getMock()).isSameAs(fixture.mock);
    }

    static class FixtureInjecting {
        @CdiMock List<String> mock;

        @InjectMocks Fixture.Holder holder;
    }

    @Test
    public void should_not_allow_direct_and_distinct_meta_mock_on_same_field() {
        Object instance =
                new Object() {
                    @Mock(name = "direct")
                    @NamedMock
                    List<String> list;
                };

        assertThatThrownBy(() -> MockitoAnnotations.openMocks(instance))
                .isInstanceOf(MockitoException.class)
                .hasMessageContaining(
                        "You cannot have more than one Mockito annotation on a field");
    }

    @Test
    public void should_not_allow_meta_mock_and_spy_on_same_field() {
        Object instance =
                new Object() {
                    @Spy @CdiMock List<String> list;
                };

        assertThatThrownBy(() -> MockitoAnnotations.openMocks(instance))
                .isInstanceOf(MockitoException.class);
    }

    @Test
    public void should_not_allow_meta_inject_mocks_and_mock_on_same_field() {
        Object instance =
                new Object() {
                    @Mock @CdiInjectMocks Fixture.Holder holder;
                };

        assertThatThrownBy(() -> MockitoAnnotations.openMocks(instance))
                .isInstanceOf(MockitoException.class);
    }
}
