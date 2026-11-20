/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.configuration.injection;

import static org.mockito.internal.util.collections.Sets.newMockSafeHashSet;
import static org.mockito.internal.util.reflection.SuperTypesLastSorter.sortSuperTypesLast;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mockito.internal.configuration.injection.filter.MockCandidateFilter;
import org.mockito.internal.configuration.injection.filter.NameBasedCandidateFilter;
import org.mockito.internal.configuration.injection.filter.TerminalMockCandidateFilter;
import org.mockito.internal.configuration.injection.filter.TypeBasedCandidateFilter;
import org.mockito.internal.configuration.plugins.Plugins;
import org.mockito.internal.util.MockUtil;
import org.mockito.internal.util.reflection.FieldReader;
import org.mockito.plugins.MemberAccessor;

/**
 * Inject the mocks declared in the test into the mocks that are held by an &#064;InjectMocks
 * instance.
 *
 * <p>
 * This allows for nested injection. Given the following test:
 * </p>
 * <pre class="code"><code class="java">
 *   &#064;Mock private MockObject1 mockObject1;
 *   &#064;Mock private MockObject2 mockObject2; // declares a field of type MockObject1
 *
 *   &#064;InjectMocks private SystemUnderTest systemUnderTest; // declares a field of type MockObject2
 * </code></pre>
 * <p>
 * <code>mockObject2</code> is injected into <code>systemUnderTest</code> as usual, and then the
 * other mock <code>mockObject1</code> is injected into <code>mockObject2</code>.
 * </p>
 *
 * <p>
 * Mocks are injected by field access only: invoking a property setter on a mock would merely
 * record an invocation and leave the field untouched. As with regular injection, mocks are
 * resolved by type first and then by name if several mocks match a field type. Nested injection
 * is best effort, i.e. a field that cannot be resolved or written to is skipped instead of failing
 * the injection that already happened.
 * </p>
 */
public class NestedInjection extends MockInjectionStrategy {

    private final MockCandidateFilter mockCandidateFilter =
            new TypeBasedCandidateFilter(
                    new NameBasedCandidateFilter(new TerminalMockCandidateFilter(false)));

    @Override
    protected boolean processInjection(
            Field injectMocksField, Object injectMocksFieldOwner, Set<Object> mockCandidates) {
        Object injectee = new FieldReader(injectMocksFieldOwner, injectMocksField).read();
        if (injectee == null) {
            return false;
        }

        Set<Object> visitedMocks = newMockSafeHashSet();
        injectIntoMocksHeldBy(injectee, mockCandidates, visitedMocks);
        return false;
    }

    private void injectIntoMocksHeldBy(
            Object injectee, Set<Object> mockCandidates, Set<Object> visitedMocks) {
        Class<?> clazz = injectee.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object value = readField(field, injectee);
                if (value != null
                        && mockCandidates.contains(value)
                        && isNestedInjectionTarget(value)) {
                    injectIntoMock(value, field, mockCandidates, visitedMocks);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private void injectIntoMock(
            Object mock,
            Field mockHoldingField,
            Set<Object> mockCandidates,
            Set<Object> visitedMocks) {
        if (!visitedMocks.add(mock)) {
            return;
        }

        Class<?> typeToMock = MockUtil.getMockSettings(mock).getTypeToMock();
        Set<Object> candidates = newMockSafeHashSet(mockCandidates);
        candidates.remove(mock);

        Class<?> mockClass = typeToMock;
        while (mockClass != null && mockClass != Object.class) {
            injectMockCandidates(mockClass, mock, mockHoldingField, newMockSafeHashSet(candidates));
            mockClass = mockClass.getSuperclass();
        }

        // mocks injected into a mock may in turn depend on other mocks
        injectIntoMocksHeldBy(mock, mockCandidates, visitedMocks);
    }

    private void injectMockCandidates(
            Class<?> awaitingInjectionClazz,
            Object injectee,
            Field mockHoldingField,
            Set<Object> candidates) {
        List<Field> orderedCandidateInjecteeFields =
                orderedInstanceFieldsFrom(awaitingInjectionClazz);
        // two passes, as in PropertyAndSetterInjection
        injectMockCandidatesOnFields(
                candidates, injectee, mockHoldingField, orderedCandidateInjecteeFields);
        injectMockCandidatesOnFields(
                candidates, injectee, mockHoldingField, orderedCandidateInjecteeFields);
    }

    private void injectMockCandidatesOnFields(
            Set<Object> candidates,
            Object injectee,
            Field mockHoldingField,
            List<Field> orderedCandidateInjecteeFields) {
        for (Iterator<Field> it = orderedCandidateInjecteeFields.iterator(); it.hasNext(); ) {
            Field candidateField = it.next();
            Object injected;
            try {
                injected =
                        mockCandidateFilter
                                .filterCandidate(
                                        candidates,
                                        candidateField,
                                        orderedCandidateInjecteeFields,
                                        injectee,
                                        mockHoldingField)
                                .thenInject();
            } catch (RuntimeException e) {
                // nested injection is best effort, e.g. an ambiguous candidate is not injected
                continue;
            }
            if (injected != null) {
                candidates.remove(injected);
                it.remove();
            }
        }
    }

    private boolean isNestedInjectionTarget(Object mock) {
        if (!MockUtil.isMock(mock) || mock instanceof Class) {
            // Class instances represent static mocks, which cannot hold fields
            return false;
        }
        try {
            Class<?> typeToMock = MockUtil.getMockSettings(mock).getTypeToMock();
            // interface mocks are proxies without any field to inject into
            return typeToMock != null && !typeToMock.isInterface();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Object readField(Field field, Object target) {
        try {
            MemberAccessor accessor = Plugins.getMemberAccessor();
            return accessor.get(field, target);
        } catch (Exception e) {
            // the field might not be readable, e.g. a private field of a JDK class
            return null;
        }
    }

    private List<Field> orderedInstanceFieldsFrom(Class<?> awaitingInjectionClazz) {
        return sortSuperTypesLast(
                Arrays.stream(awaitingInjectionClazz.getDeclaredFields())
                        .filter(
                                field ->
                                        !Modifier.isFinal(field.getModifiers())
                                                && !Modifier.isStatic(field.getModifiers()))
                        .collect(Collectors.toList()));
    }
}
