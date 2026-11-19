/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.classloading;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.Assume;
import org.junit.Test;

/**
 * Regression test for <a href="https://github.com/mockito/mockito/issues/3769">#3769</a> and <a
 * href="https://github.com/mockito/mockito/issues/2203">#2203</a>: Mockito being loaded by a
 * container class loader (Payara, WildFly, OSGi, Arquillian in-container tests) that resolves
 * classes and resources from its own, repackaged copy of the Mockito jar which does not contain
 * the {@code inject-MockMethodDispatcher.raw} resource. Previously this aborted the
 * initialization of the inline mock maker with a {@code ClassNotFoundException} for {@code
 * org.mockito.internal.creation.bytebuddy.inject.MockMethodDispatcher}. Mockito now falls back to
 * resolving the dispatcher's class file from the system class loader.
 *
 * <p>This test must run in a JVM that has a well-formed Mockito jar on the system class path,
 * which is provided by the {@code containerClassLoaderTest} Gradle task.
 */
public class ContainerClassLoaderTest {

    private static final String MOCKITO_JAR_PROPERTY = "test.mockito.jar";

    private static final String RAW_RESOURCE =
            "org/mockito/internal/creation/bytebuddy/inject-MockMethodDispatcher.raw";

    /**
     * Creates a copy of the Mockito jar as a container might provide it: the raw dispatcher
     * resource is stripped, as it is not a class file and is therefore frequently lost when a
     * deployment is assembled or merged by a build tool.
     */
    private static Path strippedMockitoJar(Path mockitoJar) throws IOException {
        Path stripped = Files.createTempFile("container-mockito", ".jar");
        try (JarFile in = new JarFile(mockitoJar.toFile());
                JarOutputStream out = new JarOutputStream(Files.newOutputStream(stripped))) {
            Enumeration<JarEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (RAW_RESOURCE.equals(entry.getName())) {
                    continue;
                }
                out.putNextEntry(new JarEntry(entry.getName()));
                try (InputStream inputStream = in.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        out.write(buffer, 0, length);
                    }
                }
            }
        }
        return stripped;
    }

    /**
     * Resolves the jars of the given classes on the system class path, to be provided to the
     * emulated container loader.
     */
    private static URL[] codeSourceUrlsOf(Class<?>... classes) {
        List<URL> urls = new ArrayList<>();
        for (Class<?> type : classes) {
            urls.add(type.getProtectionDomain().getCodeSource().getLocation());
        }
        return urls.toArray(new URL[0]);
    }

    @Test
    public void inline_mock_maker_resolves_dispatcher_when_container_jar_lacks_raw_resource()
            throws Throwable {
        String jarProperty = System.getProperty(MOCKITO_JAR_PROPERTY);
        Assume.assumeNotNull(jarProperty);
        Assume.assumeFalse(jarProperty.trim().isEmpty());

        Path mockitoJar = Paths.get(jarProperty);
        Path containerJar = strippedMockitoJar(mockitoJar);

        List<URL> urls = new ArrayList<>();
        urls.add(containerJar.toUri().toURL());
        for (URL url :
                codeSourceUrlsOf(
                        net.bytebuddy.ByteBuddy.class,
                        net.bytebuddy.agent.ByteBuddyAgent.class,
                        org.objenesis.Objenesis.class)) {
            urls.add(url);
        }

        // A container loader that only resolves resources from its own jars.
        ClassLoader container =
                new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader()) {
                    @Override
                    public URL getResource(String name) {
                        URL url = findResource(name);
                        return url != null ? url : super.getResource(name);
                    }
                };

        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(container);
        try {
            Class<?> mockito = Class.forName("org.mockito.Mockito", true, container);
            Method mock = mockito.getMethod("mock", Class.class);
            Object value = mock.invoke(null, Runnable.class);

            assertThat(value).isNotNull();
            assertThat(value.getClass().getClassLoader()).isSameAs(container);
        } finally {
            Thread.currentThread().setContextClassLoader(contextLoader);
        }
    }
}
