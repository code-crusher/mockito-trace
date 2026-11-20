/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.bugs.injection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Issue 3702 : mocks should also be injected into the mocks that are injected into an
 * &#064;InjectMocks instance.
 */
@RunWith(MockitoJUnitRunner.class)
public class Issue3702NestedInjectionTest {

    @Mock private MockObject1 mockObject1;
    @Mock private MockObject2 mockObject2;

    @InjectMocks private SystemUnderTest systemUnderTest = new SystemUnderTest();

    @Test
    public void should_inject_mock_into_mock() {
        assertThat(systemUnderTest.mockObject2).isSameAs(mockObject2);
        assertThat(mockObject2.mockObject1).isSameAs(mockObject1);
    }

    static class MockObject1 {}

    static class MockObject2 {
        MockObject1 mockObject1;
    }

    static class SystemUnderTest {
        MockObject2 mockObject2;
    }

    // nested injection is applied recursively

    @Mock private Leaf leaf;
    @Mock private Middle middle;

    @InjectMocks private Root root = new Root();

    @Test
    public void should_inject_nested_mocks_recursively() {
        assertThat(root.middle).isSameAs(middle);
        assertThat(middle.leaf).isSameAs(leaf);
    }

    static class Leaf {}

    static class Middle {
        Leaf leaf;
    }

    static class Root {
        Middle middle;
    }

    // nested injection resolves mocks by name when several mocks match the field type

    @Mock private List<String> firstList;
    @Mock private List<String> secondList;

    @InjectMocks private HolderOfNamedLists holder = new HolderOfNamedLists();

    @Test
    public void should_resolve_nested_mocks_by_name() {
        assertThat(holder.firstList).isSameAs(firstList);
        assertThat(holder.secondList).isSameAs(secondList);
    }

    static class HolderOfNamedLists {
        List<String> firstList;
        List<String> secondList;
    }

    // nested injection works through the type hierarchy of the mock

    @Mock private Service service;

    @InjectMocks private Client client = new Client();

    @Test
    public void should_inject_nested_mocks_declared_in_super_types() {
        assertThat(client.service).isSameAs(service);
        assertThat(((AbstractService) service).dependency).isSameAs(mockObject1);
    }

    static class Client {
        Service service;
    }

    static class Service extends AbstractService {}

    abstract static class AbstractService {
        MockObject1 dependency;
    }

    // mocks are injected into spies as well, and the spy still behaves as a real object

    @Spy private Collaborator collaborator = new Collaborator();

    @InjectMocks private SpiedClient spiedClient = new SpiedClient();

    @Test
    public void should_inject_mocks_into_spies() {
        assertThat(spiedClient.collaborator).isSameAs(collaborator);
        assertThat(collaborator.dependency).isSameAs(mockObject1);
        assertThat(collaborator.work()).isEqualTo("done");
    }

    static class SpiedClient {
        Collaborator collaborator;
    }

    static class Collaborator {
        MockObject1 dependency;

        String work() {
            return "done";
        }
    }

    // interface mocks cannot hold injected mocks, which must not fail the injection

    @Mock private Runnable runnable;

    @InjectMocks private HolderOfInterface holderOfInterface = new HolderOfInterface();

    @Test
    public void should_not_fail_for_interface_mocks() {
        assertThat(holderOfInterface.runnable).isSameAs(runnable);
    }

    static class HolderOfInterface {
        Runnable runnable;
    }

    // mocks are also injected into the mocks held by a spy that is itself an @InjectMocks field

    @Spy @InjectMocks private SpiedRoot spiedRoot = new SpiedRoot();

    @Test
    public void should_inject_mocks_into_injected_spies() {
        assertThat(spiedRoot.middle).isSameAs(middle);
        assertThat(middle.leaf).isSameAs(leaf);
    }

    static class SpiedRoot {
        Middle middle;

        String work() {
            return "done";
        }
    }

    // cyclic references between mocks are not followed indefinitely

    @Mock private CyclicA cyclicA;
    @Mock private CyclicB cyclicB;

    @InjectMocks private CyclicRoot cyclicRoot = new CyclicRoot();

    @Test
    public void should_handle_cyclic_references_between_mocks() {
        assertThat(cyclicRoot.cyclicA).isSameAs(cyclicA);
        assertThat(cyclicA.cyclicB).isSameAs(cyclicB);
        assertThat(cyclicB.cyclicA).isSameAs(cyclicA);
    }

    static class CyclicRoot {
        CyclicA cyclicA;
    }

    static class CyclicA {
        CyclicB cyclicB;
    }

    static class CyclicB {
        CyclicA cyclicA;
    }

    // nested injection also applies to objects created by constructor injection

    @Mock private ConstructorDependency constructorDependency;

    @InjectMocks private ConstructorRoot constructorRoot;

    @Test
    public void should_inject_nested_mocks_with_constructor_injection() {
        assertThat(constructorRoot.constructorDependency).isSameAs(constructorDependency);
        assertThat(constructorDependency.leaf).isSameAs(leaf);
    }

    static class ConstructorRoot {
        private final ConstructorDependency constructorDependency;

        ConstructorRoot(ConstructorDependency constructorDependency) {
            this.constructorDependency = constructorDependency;
        }
    }

    static class ConstructorDependency {
        Leaf leaf;
    }
}
