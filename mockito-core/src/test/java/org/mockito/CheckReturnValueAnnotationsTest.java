/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Verifies the static-analysis annotations introduced for issue #3796: {@link CheckReturnValue}
 * on packages and {@link CanIgnoreReturnValue} on fluent APIs whose return value may legitimately
 * be ignored. These annotations drive Error Prone's {@code CheckReturnValue} check; this test
 * guards against accidental removal.
 *
 * <p>Both annotations use {@code CLASS} retention, so they are not visible to runtime reflection.
 * The assertions therefore inspect the compiled class files directly: an annotation referenced
 * from a class file appears in its constant pool as a UTF-8 entry holding the descriptor string
 * (e.g. {@code "Lorg/mockito/CheckReturnValue;"}).
 */
public class CheckReturnValueAnnotationsTest {

    private static final String CHECK_RETURN_VALUE = "Lorg/mockito/CheckReturnValue;";
    private static final String CAN_IGNORE_RETURN_VALUE = "Lorg/mockito/CanIgnoreReturnValue;";

    @Test
    public void check_return_value_annotation_exists() {
        assertNotNull(classFile("org.mockito.CheckReturnValue"));
    }

    @Test
    public void can_ignore_return_value_annotation_exists() {
        assertNotNull(classFile("org.mockito.CanIgnoreReturnValue"));
    }

    @Test
    public void root_package_declares_check_return_value() {
        assertPackageDeclaresCheckReturnValue("org.mockito");
    }

    /**
     * Package-level annotations are not inherited by sub-packages, therefore every package needs
     * its own annotated {@code package-info.java}. This covers a representative sample of the
     * packages that had no {@code package-info.java} before issue #3796.
     */
    @Test
    public void all_packages_declare_check_return_value() {
        assertPackageDeclaresCheckReturnValue("org.mockito.configuration");
        assertPackageDeclaresCheckReturnValue("org.mockito.creation");
        assertPackageDeclaresCheckReturnValue("org.mockito.creation.instance");
        assertPackageDeclaresCheckReturnValue("org.mockito.hamcrest");
        assertPackageDeclaresCheckReturnValue("org.mockito.internal.configuration.injection");
        assertPackageDeclaresCheckReturnValue("org.mockito.internal.creation.settings");
        assertPackageDeclaresCheckReturnValue("org.mockito.internal.exceptions");
        assertPackageDeclaresCheckReturnValue("org.mockito.internal.framework");
        assertPackageDeclaresCheckReturnValue("org.mockito.internal.invocation");
        assertPackageDeclaresCheckReturnValue("org.mockito.internal.matchers.apachecommons");
        assertPackageDeclaresCheckReturnValue("org.mockito.internal.util.reflection");
        assertPackageDeclaresCheckReturnValue("org.mockito.junit");
        assertPackageDeclaresCheckReturnValue("org.mockito.session");
        assertPackageDeclaresCheckReturnValue("org.mockito.stubbing");
        assertPackageDeclaresCheckReturnValue("org.mockito.verification");
    }

    /**
     * The APIs listed in issue #3796 that "return this" must be annotated with {@link
     * CanIgnoreReturnValue} so Error Prone does not force callers to use their fluent results.
     */
    @Test
    public void fluent_builder_apis_can_ignore_return_value() {
        assertReferencesCanIgnoreReturnValue(
                "org.mockito.internal.creation.settings.CreationSettings");
        assertReferencesCanIgnoreReturnValue("org.mockito.junit.MockitoRule");
        assertReferencesCanIgnoreReturnValue("org.mockito.stubbing.BaseStubber");
        assertReferencesCanIgnoreReturnValue(
                "org.mockito.internal.matchers.apachecommons.EqualsBuilder");
        assertReferencesCanIgnoreReturnValue("org.mockito.session.MockitoSessionBuilder");
        assertReferencesCanIgnoreReturnValue(
                "org.mockito.internal.configuration.injection.MockInjection$OngoingMockInjection");
        assertReferencesCanIgnoreReturnValue("org.mockito.junit.VerificationCollector");
        assertReferencesCanIgnoreReturnValue("org.mockito.MockitoFramework");
        assertReferencesCanIgnoreReturnValue("org.mockito.stubbing.OngoingStubbing");
    }

    private static void assertPackageDeclaresCheckReturnValue(String packageName) {
        String packageInfo = classFile(packageName + ".package-info");
        assertNotNull(
                "package-info.class for " + packageName + " must be on the test classpath",
                packageInfo);
        assertTrue(
                packageName + " must be annotated with @CheckReturnValue",
                packageInfo.contains(CHECK_RETURN_VALUE));
    }

    private static void assertReferencesCanIgnoreReturnValue(String binaryName) {
        String classFile = classFile(binaryName);
        assertNotNull("class file for " + binaryName + " must be on the test classpath", classFile);
        assertTrue(
                binaryName + " must reference @CanIgnoreReturnValue",
                classFile.contains(CAN_IGNORE_RETURN_VALUE));
    }

    private static String classFile(String binaryName) {
        String resource = binaryName.replace('.', '/') + ".class";
        try (InputStream in =
                CheckReturnValueAnnotationsTest.class
                        .getClassLoader()
                        .getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n = in.read(buffer); n != -1; n = in.read(buffer)) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + resource, e);
        }
    }
}
