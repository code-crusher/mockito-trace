/*
 * Copyright (c) 2017 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.creation.bytebuddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.description.modifier.TypeManifestation;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import org.junit.Test;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.plugins.MockMaker;

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Observable;
import java.util.Observer;

import static net.bytebuddy.jar.asm.Opcodes.ACC_ABSTRACT;
import static net.bytebuddy.jar.asm.Opcodes.ACC_INTERFACE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_PUBLIC;
import static net.bytebuddy.jar.asm.Opcodes.V1_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockitoutil.ClassLoaders.inMemoryClassLoader;

public class SubclassByteBuddyMockMakerTest
        extends AbstractByteBuddyMockMakerTest<SubclassByteBuddyMockMaker> {

    public SubclassByteBuddyMockMakerTest() {
        super(new SubclassByteBuddyMockMaker());
    }

    @Test
    public void is_type_mockable_excludes_primitive_wrapper_classes() {
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(Integer.class);
        assertThat(mockable.mockable()).isFalse();
        assertThat(mockable.nonMockableReason()).contains("final");
    }

    @Test
    public void is_type_mockable_excludes_sealed_classes() {
        if (ClassFileVersion.ofThisVm().isAtMost(ClassFileVersion.JAVA_V16)) {
            return;
        }
        DynamicType.Builder<?> base = new ByteBuddy().subclass(Object.class);
        DynamicType.Builder<?> subclass =
                new ByteBuddy().subclass(base.toTypeDescription()).merge(TypeManifestation.FINAL);
        Class<?> type =
                base.permittedSubclass(subclass.toTypeDescription())
                        .make()
                        .include(subclass.make())
                        .load(null)
                        .getLoaded();
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(type);
        assertThat(mockable.mockable()).isFalse();
        assertThat(mockable.nonMockableReason()).contains("sealed");
    }

    @Test
    public void is_type_mockable_excludes_primitive_classes() {
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(int.class);
        assertThat(mockable.mockable()).isFalse();
        assertThat(mockable.nonMockableReason()).contains("primitive");
    }

    @Test
    public void is_type_mockable_allow_anonymous() {
        Observer anonymous =
                new Observer() {
                    @Override
                    public void update(Observable o, Object arg) {}
                };
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(anonymous.getClass());
        assertThat(mockable.mockable()).isTrue();
        assertThat(mockable.nonMockableReason()).contains("");
    }

    @Test
    public void is_type_mockable_give_empty_reason_if_type_is_mockable() {
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(SomeClass.class);
        assertThat(mockable.mockable()).isTrue();
        assertThat(mockable.nonMockableReason()).isEqualTo("");
    }

    @Test
    public void mock_class_with_annotations() throws Exception {
        MockSettingsImpl<ClassWithAnnotation> mockSettings = new MockSettingsImpl<>();
        mockSettings.setTypeToMock(ClassWithAnnotation.class);

        ClassWithAnnotation proxy = mockMaker.createMock(mockSettings, dummyHandler());

        assertThat(proxy.getClass().isAnnotationPresent(SampleAnnotation.class)).isTrue();
        assertThat(proxy.getClass().getAnnotation(SampleAnnotation.class).value()).isEqualTo("foo");

        assertThat(
                        proxy.getClass()
                                .getMethod("sampleMethod")
                                .isAnnotationPresent(SampleAnnotation.class))
                .isTrue();
        assertThat(
                        proxy.getClass()
                                .getMethod("sampleMethod")
                                .getAnnotation(SampleAnnotation.class)
                                .value())
                .isEqualTo("bar");
    }

    @Test
    public void mock_class_with_annotations_with_additional_interface() throws Exception {
        MockSettingsImpl<ClassWithAnnotation> mockSettings = new MockSettingsImpl<>();
        mockSettings.setTypeToMock(ClassWithAnnotation.class);
        mockSettings.extraInterfaces(Serializable.class);

        ClassWithAnnotation proxy = mockMaker.createMock(mockSettings, dummyHandler());

        assertThat(proxy.getClass().isAnnotationPresent(SampleAnnotation.class)).isTrue();
        assertThat(proxy.getClass().getAnnotation(SampleAnnotation.class).value()).isEqualTo("foo");

        assertThat(
                        proxy.getClass()
                                .getMethod("sampleMethod")
                                .isAnnotationPresent(SampleAnnotation.class))
                .isTrue();
        assertThat(
                        proxy.getClass()
                                .getMethod("sampleMethod")
                                .getAnnotation(SampleAnnotation.class)
                                .value())
                .isEqualTo("bar");
    }

    @Test
    public void mock_interface_with_annotations() throws Exception {
        MockSettingsImpl<InterfaceWithAnnotation> mockSettings = new MockSettingsImpl<>();
        mockSettings.setTypeToMock(InterfaceWithAnnotation.class);

        InterfaceWithAnnotation proxy = mockMaker.createMock(mockSettings, dummyHandler());

        assertThat(proxy.getClass().isAnnotationPresent(SampleAnnotation.class)).isTrue();
        assertThat(proxy.getClass().getAnnotation(SampleAnnotation.class).value()).isEqualTo("foo");

        assertThat(
                        proxy.getClass()
                                .getMethod("sampleMethod")
                                .isAnnotationPresent(SampleAnnotation.class))
                .isTrue();
        assertThat(
                        proxy.getClass()
                                .getMethod("sampleMethod")
                                .getAnnotation(SampleAnnotation.class)
                                .value())
                .isEqualTo("bar");
    }

    @Test
    public void mock_interface_with_annotations_with_additional_interface() throws Exception {
        MockSettingsImpl<InterfaceWithAnnotation> mockSettings = new MockSettingsImpl<>();
        mockSettings.setTypeToMock(InterfaceWithAnnotation.class);
        mockSettings.extraInterfaces(Serializable.class);

        InterfaceWithAnnotation proxy = mockMaker.createMock(mockSettings, dummyHandler());

        assertThat(proxy.getClass().isAnnotationPresent(SampleAnnotation.class)).isFalse();

        assertThat(
                        proxy.getClass()
                                .getMethod("sampleMethod")
                                .isAnnotationPresent(SampleAnnotation.class))
                .isTrue();
        assertThat(
                        proxy.getClass()
                                .getMethod("sampleMethod")
                                .getAnnotation(SampleAnnotation.class)
                                .value())
                .isEqualTo("bar");
    }

    @Test
    public void mock_type_without_annotations() throws Exception {
        MockSettingsImpl<ClassWithAnnotation> mockSettings = new MockSettingsImpl<>();
        mockSettings.setTypeToMock(ClassWithAnnotation.class);
        mockSettings.withoutAnnotations();

        ClassWithAnnotation proxy = mockMaker.createMock(mockSettings, dummyHandler());

        assertThat(proxy.getClass().isAnnotationPresent(SampleAnnotation.class)).isFalse();
        assertThat(
                        proxy.getClass()
                                .getMethod("sampleMethod")
                                .isAnnotationPresent(SampleAnnotation.class))
                .isFalse();
    }

    @Override
    protected Class<?> mockTypeOf(Class<?> type) {
        return type.getSuperclass();
    }

    /**
     * @see <a
     *     href="https://github.com/mockito/mockito/issues/3487">https://github.com/mockito/mockito/issues/3487</a>
     */
    @Test
    public void mock_interface_referencing_missing_class_reports_missing_class() throws Exception {
        // given an interface whose method signature references a class that is not on the
        // classpath, mimicking an incomplete dependency setup such as a partial servlet-api jar
        ClassLoader classLoaderWithoutReferencedClass =
                inMemoryClassLoader()
                        .withClassDefinition(
                                "foo.ReferencingInterface", interfaceReferencingMissingClass())
                        .build();
        Class<?> referencing =
                classLoaderWithoutReferencedClass.loadClass("foo.ReferencingInterface");

        MockSettingsImpl<?> mockSettings = new MockSettingsImpl<>();
        //noinspection rawtypes
        mockSettings.setTypeToMock((Class) referencing);

        // when
        try {
            //noinspection unchecked
            mockMaker.createMock(mockSettings, dummyHandler());
            fail("Expected a MockitoException");
        } catch (MockitoException e) {
            // then the error message points at the missing class instead of suggesting
            // a final or private class problem
            assertThat(e)
                    .hasMessageContaining("a class it references is missing from the classpath")
                    .hasMessageContaining("foo.Missing");
        }
    }

    /**
     * Generates the bytecode of an interface with a method whose return type is
     * <code>foo.Missing</code>, a class that is not defined anywhere.
     */
    private static byte[] interfaceReferencingMissingClass() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(
                V1_8,
                ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT,
                "foo/ReferencingInterface",
                null,
                "java/lang/Object",
                null);
        MethodVisitor methodVisitor =
                classWriter.visitMethod(
                        ACC_PUBLIC | ACC_ABSTRACT, "getValue", "()Lfoo/Missing;", null, null);
        methodVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface SampleAnnotation {

        String value();
    }

    @SampleAnnotation("foo")
    public static class ClassWithAnnotation {

        @SampleAnnotation("bar")
        public void sampleMethod() {
            throw new UnsupportedOperationException();
        }
    }

    @SampleAnnotation("foo")
    public interface InterfaceWithAnnotation {

        @SampleAnnotation("bar")
        void sampleMethod();
    }
}
