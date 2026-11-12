/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.creation.bytebuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.lang.instrument.Instrumentation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.utility.OpenedClassReader;
import org.junit.Test;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.util.concurrent.DetachedThreadLocal;
import org.mockito.internal.util.concurrent.WeakConcurrentMap;

public class InlineBytecodeGeneratorTest {

    @Test
    public void clearing_does_not_transform_classes_on_another_thread() throws Exception {
        Instrumentation instrumentation = mock(Instrumentation.class);
        InlineBytecodeGenerator generator = newGenerator(instrumentation);
        byte[] original = ClassFileLocator.ForClassLoader.read(Sample.class);
        generator.mockClassConstruction(Sample.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            doAnswer(
                            invocation -> {
                                assertThat(
                                                executor.submit(
                                                                () ->
                                                                        transform(
                                                                                generator,
                                                                                original))
                                                        .get(10, TimeUnit.SECONDS))
                                        .isNull();
                                return null;
                            })
                    .when(instrumentation)
                    .retransformClasses(any(Class[].class));

            generator.clearAllCaches();

            assertThat(transform(generator, original)).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void transformation_failures_are_scoped_to_their_operation() throws Exception {
        Instrumentation instrumentation = mock(Instrumentation.class);
        InlineBytecodeGenerator generator = newGenerator(instrumentation);
        byte[] original = ClassFileLocator.ForClassLoader.read(Sample.class);
        generator.mockClassConstruction(Sample.class);
        assertThat(transform(generator, new byte[0])).isNull();
        doAnswer(
                        invocation -> {
                            assertThat(transform(generator, original)).isNotNull();
                            return null;
                        })
                .when(instrumentation)
                .retransformClasses(any(Class[].class));

        generator.clearAllCaches();
        generator.mockClassConstruction(Sample.class);
        generator.clearAllCaches();

        doAnswer(
                        invocation -> {
                            assertThat(transform(generator, new byte[0])).isNull();
                            return null;
                        })
                .when(instrumentation)
                .retransformClasses(any(Class[].class));

        assertThatThrownBy(() -> generator.mockClassConstruction(Sample.class))
                .isInstanceOf(MockitoException.class)
                .hasMessageContaining("Could not modify all classes");
    }

    @Test
    public void clearing_reports_failure_from_its_transformation() throws Exception {
        Instrumentation instrumentation = mock(Instrumentation.class);
        InlineBytecodeGenerator generator = newGenerator(instrumentation);
        byte[] original = ClassFileLocator.ForClassLoader.read(Sample.class);
        generator.mockClassConstruction(Sample.class);
        doAnswer(
                        invocation -> {
                            assertThat(transform(generator, new byte[0])).isNull();
                            return null;
                        })
                .when(instrumentation)
                .retransformClasses(any(Class[].class));

        assertThatThrownBy(generator::clearAllCaches)
                .isInstanceOf(MockitoException.class)
                .hasMessageContaining("Could not reset all classes");
        assertThat(transform(generator, original)).isNull();
    }

    /**
     * Verifies that the advice injected at the beginning of an instrumented method is not mapped
     * to the first source line of that method. Otherwise, a debugger stops at a breakpoint on
     * that line even though the mocked method body is never executed, see #3772.
     */
    @Test
    public void instrumentation_does_not_map_injected_code_to_the_first_line() throws Exception {
        Instrumentation instrumentation = mock(Instrumentation.class);
        InlineBytecodeGenerator generator = newGenerator(instrumentation);
        byte[] original = ClassFileLocator.ForClassLoader.read(Sample.class);
        generator.mockClassConstruction(Sample.class);

        byte[] transformed =
                generator.transform(
                        Sample.class.getClassLoader(),
                        Sample.class.getName(),
                        Sample.class,
                        null,
                        original);

        Map<Integer, Integer> originalLines = lineNumberOffsets(original, "doSomething");
        Map<Integer, Integer> transformedLines = lineNumberOffsets(transformed, "doSomething");
        assertThat(originalLines).isNotEmpty();
        assertThat(originalLines.get(firstLine(originalLines))).isZero();
        // All original line numbers are retained, but the first one must no longer be mapped to
        // the beginning of the method where the dispatch advice is inlined.
        assertThat(transformedLines.keySet()).isEqualTo(originalLines.keySet());
        assertThat(transformedLines.get(firstLine(originalLines))).isGreaterThan(0);
        // The remaining line numbers are shifted, but their relative order is preserved.
        assertThat(transformedLines.get(firstLine(originalLines)))
                .isLessThan(transformedLines.get(lastLine(originalLines)));
    }

    private static int firstLine(Map<Integer, Integer> lines) {
        return lines.keySet().stream().min(Integer::compareTo).orElseThrow(AssertionError::new);
    }

    private static int lastLine(Map<Integer, Integer> lines) {
        return lines.keySet().stream().max(Integer::compareTo).orElseThrow(AssertionError::new);
    }

    /**
     * Returns the source line numbers of the given method mapped to their bytecode offsets.
     */
    private static Map<Integer, Integer> lineNumberOffsets(byte[] classFile, String methodName) {
        Map<Integer, Integer> lines = new LinkedHashMap<>();
        new ClassReader(classFile)
                .accept(
                        new ClassVisitor(OpenedClassReader.ASM_API, new ClassWriter(0)) {
                            @Override
                            public MethodVisitor visitMethod(
                                    int access,
                                    String name,
                                    String descriptor,
                                    String signature,
                                    String[] exceptions) {
                                MethodVisitor delegate =
                                        super.visitMethod(
                                                access, name, descriptor, signature, exceptions);
                                if (!methodName.equals(name)) {
                                    return delegate;
                                }
                                return new MethodVisitor(OpenedClassReader.ASM_API, delegate) {
                                    @Override
                                    public void visitLineNumber(int line, Label start) {
                                        lines.put(line, start.getOffset());
                                        super.visitLineNumber(line, start);
                                    }
                                };
                            }
                        },
                        0);
        return lines;
    }

    private static InlineBytecodeGenerator newGenerator(Instrumentation instrumentation) {
        new InlineByteBuddyMockMaker();
        return new InlineBytecodeGenerator(
                instrumentation,
                new WeakConcurrentMap<>(false),
                new DetachedThreadLocal<>(DetachedThreadLocal.Cleaner.MANUAL),
                new DetachedThreadLocal<>(DetachedThreadLocal.Cleaner.MANUAL),
                type -> false,
                (type, object, arguments, parameterTypeNames) -> object);
    }

    private static byte[] transform(InlineBytecodeGenerator generator, byte[] bytes) {
        return generator.transform(
                Sample.class.getClassLoader(), Sample.class.getName(), Sample.class, null, bytes);
    }

    static class Sample {
        public String doSomething() {
            System.out.println("Hello 1");
            System.out.println("Hello 2");
            return "hello!";
        }
    }
}
