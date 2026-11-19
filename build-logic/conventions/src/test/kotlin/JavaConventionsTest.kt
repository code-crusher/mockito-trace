import java.nio.file.Path

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class JavaConventionsTest {
    @TempDir
    lateinit var projectDirectory: Path

    @BeforeEach
    fun setUp() {
        projectDirectory.resolve("settings.gradle.kts").toFile().writeText(
            """
            rootProject.name = "java-conventions-test"
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        version("jacoco", "0.8.14")
                    }
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun compiles_with_the_release_flag_instead_of_only_source_and_target_compatibility() {
        projectDirectory.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                java
                id("mockito.java-conventions")
            }

            tasks.register("verifyRelease") {
                val compileJava = tasks.named<JavaCompile>("compileJava")
                val compileTestJava = tasks.named<JavaCompile>("compileTestJava")
                doLast {
                    check(compileJava.get().options.release.get() == 11)
                    check(compileTestJava.get().options.release.get() == 11)
                }
            }
            """.trimIndent()
        )
        writeSource(
            """
            package example;

            public class Example {}
            """.trimIndent()
        )

        val result = runner("verifyRelease", "compileJava").build()

        assertThat(result.task(":verifyRelease")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun rejects_apis_newer_than_the_release_version() {
        // On an older JDK the probe API is missing regardless of --release, so
        // the test could only pass for the wrong reason.
        assumeTrue(Runtime.version().feature() >= 17)

        projectDirectory.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                java
                id("mockito.java-conventions")
            }
            """.trimIndent()
        )
        writeSource(
            """
            package example;

            public class Example {
                public String hex(byte[] bytes) {
                    // java.util.HexFormat was added in Java 17
                    return java.util.HexFormat.of().formatHex(bytes);
                }
            }
            """.trimIndent()
        )

        val result = runner("compileJava").buildAndFail()

        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("cannot find symbol")
    }

    private fun writeSource(content: String) {
        projectDirectory.resolve("src/main/java/example/Example.java").toFile().apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }

    private fun runner(vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace")
}
