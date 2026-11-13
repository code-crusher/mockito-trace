/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.bugs.creation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Regression test for <a href="https://github.com/mockito/mockito/issues/3645">issue #3645</a>.
 *
 * <p>Some compilers, for example the Kotlin compiler for 2.2.0, emit nested classes that cannot be
 * loaded by the JVM, for instance a Kotlin {@code DefaultImpls} class that declares the same field
 * twice. Mockito must not need to load such nested classes to instrument their enclosing type.
 */
public class MockClassWithUnloadableNestedTypeTest {

    private static final String OUTER = "org.mockitousage.bugs.creation.Unloadable$Outer";
    private static final String NESTED = OUTER + "$Nested";

    @Test
    public void should_mock_class_with_nested_type_that_cannot_be_loaded() throws Exception {
        Class<?> type = new UnloadableClassLoader().loadClass(OUTER);

        Object mock = Mockito.mock(type);

        assertThat(Mockito.mockingDetails(mock).isMock()).isTrue();
    }

    @Test
    public void should_spy_class_with_nested_type_that_cannot_be_loaded() throws Exception {
        Class<?> type = new UnloadableClassLoader().loadClass(OUTER);

        Object spy = Mockito.spy(type.getDeclaredConstructor().newInstance());

        assertThat(Mockito.mockingDetails(spy).isSpy()).isTrue();
    }

    /**
     * The nested types are not needed to instrument a type, but they must not get lost either: the
     * {@code InnerClasses} attribute of the retransformed class is retained. A final type is used so
     * that the inline mock maker retransforms the type itself instead of creating a subclass.
     */
    @Test
    public void should_retain_nested_types_of_instrumented_class() {
        Object mock = Mockito.mock(WithNestedType.class);

        // An inline mock is an instance of the retransformed type itself.
        assertThat(mock.getClass()).isEqualTo(WithNestedType.class);
        assertThat(WithNestedType.class.getDeclaredClasses())
                .containsExactly(WithNestedType.Nested.class);
    }

    public static final class WithNestedType {

        public static class Nested {}
    }

    /**
     * A class loader that defines an outer class with a nested class that declares a duplicate field
     * and therefore cannot be loaded by the JVM.
     */
    private static class UnloadableClassLoader extends ClassLoader {

        private final Map<String, byte[]> types = new HashMap<>();

        private UnloadableClassLoader() {
            super(MockClassWithUnloadableNestedTypeTest.class.getClassLoader());
            types.put(OUTER, outer());
            types.put(NESTED, nested());
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = types.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            // Class files can be parsed even if the JVM refuses to load the classes they represent.
            byte[] bytes = types.get(name.replace('/', '.').replace(".class", ""));
            return bytes == null
                    ? super.getResourceAsStream(name)
                    : new ByteArrayInputStream(bytes);
        }

        private static byte[] outer() {
            ClassWriter writer = new ClassWriter(0);
            writer.visit(
                    Opcodes.V1_8,
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                    OUTER.replace('.', '/'),
                    null,
                    "java/lang/Object",
                    null);
            writer.visitInnerClass(
                    NESTED.replace('.', '/'),
                    OUTER.replace('.', '/'),
                    "Nested",
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
            MethodVisitor constructor =
                    writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitMethodInsn(
                    Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(1, 1);
            constructor.visitEnd();
            writer.visitEnd();
            return writer.toByteArray();
        }

        private static byte[] nested() {
            ClassWriter writer = new ClassWriter(0);
            writer.visit(
                    Opcodes.V1_8,
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                    NESTED.replace('.', '/'),
                    null,
                    "java/lang/Object",
                    null);
            writer.visitField(Opcodes.ACC_PRIVATE, "L$0", "Ljava/lang/Object;", null, null)
                    .visitEnd();
            writer.visitField(Opcodes.ACC_PRIVATE, "L$0", "Ljava/lang/Object;", null, null)
                    .visitEnd();
            writer.visitEnd();
            return writer.toByteArray();
        }
    }
}
