import org.gradle.api.tasks.testing.Test

plugins {
    id("java")
    id("mockito.test-conventions")
}

description = "Test suite for running Mockito from isolated container class loaders"

dependencies {
    testImplementation(project(":mockito-core"))
    testImplementation(libs.junit4)
    testImplementation(libs.assertj)
    // Byte Buddy and Objenesis classes are provided to the emulated container class loader.
    testImplementation(libs.bytebuddy)
    testImplementation(libs.bytebuddy.agent)
    testImplementation(libs.objenesis)
}

tasks {
    // Runs the container class loader test with a pristine mockito-core jar on the
    // system class path, mirroring a container deployment (Payara, WildFly, OSGi)
    // that loads Mockito from its own, potentially repackaged, jar copy.
    val containerClassLoaderTest by registering(Test::class) {
        description = "Runs ContainerClassLoaderTest with the mockito jar on the system class path"
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        include("**/ContainerClassLoaderTest.class")

        val mockitoJar = project(":mockito-core").tasks.named<Jar>("jar").flatMap { it.archiveFile }
        dependsOn(mockitoJar)
        jvmArgumentProviders.add(
            CommandLineArgumentProvider {
                listOf("-Dtest.mockito.jar=${mockitoJar.get().asFile.absolutePath}")
            }
        )
    }

    test {
        dependsOn(containerClassLoaderTest)
        exclude("**/ContainerClassLoaderTest.class")
    }
}
