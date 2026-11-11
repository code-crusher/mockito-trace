import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

plugins {
    id("com.android.library")
    id("mockito.publication-conventions")
}

description = "Mockito for Android"

// dexmaker-mockito-inline packages a "dispatcher.jar" resource at the root of its classes.jar.
// mockk-agent-android packages a different, incompatible "dispatcher.jar" at the same path, which
// makes the Android Gradle Plugin fail when both libraries are on the same classpath.
// See https://github.com/mockito/mockito/issues/3863.
// To let both libraries coexist, the dexmaker artifact is repackaged with its dispatcher resource
// moved to a Mockito-specific location, so that mockito-android no longer contributes a
// root-level "dispatcher.jar".
val dispatcherJarName = "dispatcher.jar"
val relocatedDispatcherJar = "org/mockito/android/internal/creation/inline/dispatcher.jar"

val dexmakerInline = configurations.create("dexmakerInline") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class, "aar"))
    }
}

val repackagedDexmakerJar = layout.buildDirectory.file("repackaged/dexmaker-mockito-inline.jar")
val repackagedDexmakerJniLibs = layout.buildDirectory.dir("repackaged/jniLibs")

val repackageDexmakerInline = tasks.register("repackageDexmakerInline") {
    val dexmakerAar = dexmakerInline.elements.map { it.single().asFile }
    inputs.files(dexmakerAar)
    inputs.property("dispatcherJarName", dispatcherJarName)
    inputs.property("relocatedDispatcherJar", relocatedDispatcherJar)
    outputs.file(repackagedDexmakerJar)
    outputs.dir(repackagedDexmakerJniLibs)

    doLast {
        val jarFile = repackagedDexmakerJar.get().asFile
        val jniLibsDir = repackagedDexmakerJniLibs.get().asFile

        val classesJar = ZipFile(dexmakerAar.get()).use { aar ->
            aar.entries().asSequence().forEach { entry ->
                if (entry.name.startsWith("jni/") && !entry.isDirectory) {
                    val target = jniLibsDir.resolve(entry.name.removePrefix("jni/"))
                    target.parentFile.mkdirs()
                    aar.getInputStream(entry).use { input -> target.outputStream().use(input::copyTo) }
                }
            }
            aar.getInputStream(aar.getEntry("classes.jar")).readBytes()
        }

        jarFile.parentFile.mkdirs()
        ZipInputStream(classesJar.inputStream()).use { input ->
            ZipOutputStream(jarFile.outputStream()).use { output ->
                generateSequence { input.nextEntry }.forEach { entry ->
                    val name =
                        if (entry.name == dispatcherJarName) relocatedDispatcherJar else entry.name
                    var content = input.readBytes()
                    if (entry.name == "com/android/dx/mockito/inline/InlineDexmakerMockMaker.class") {
                        content = relocateDispatcherJarConstant(content, relocatedDispatcherJar)
                    }
                    output.putNextEntry(ZipEntry(name).apply { time = 0L })
                    output.write(content)
                    output.closeEntry()
                }
            }
        }
    }
}

fun relocateDispatcherJarConstant(classFile: ByteArray, relocatedDispatcherJar: String): ByteArray {
    val reader = ClassReader(classFile)
    val writer = ClassWriter(0)
    reader.accept(
        object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitField(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                value: Any?,
            ): FieldVisitor {
                val newValue = if (value == "dispatcher.jar") relocatedDispatcherJar else value
                return super.visitField(access, name, descriptor, signature, newValue)
            }

            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor {
                return object : MethodVisitor(
                    Opcodes.ASM9,
                    super.visitMethod(access, name, descriptor, signature, exceptions)
                ) {
                    override fun visitLdcInsn(value: Any?) {
                        super.visitLdcInsn(
                            if (value == "dispatcher.jar") relocatedDispatcherJar else value
                        )
                    }
                }
            }
        },
        0
    )
    return writer.toByteArray()
}

android {
    namespace = "org.mockito.android"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
    }

    publishing {
        singleVariant("release")
    }

    sourceSets {
        named("main") {
            jniLibs.srcDirs(files(repackagedDexmakerJniLibs).builtBy(repackageDexmakerInline))
        }
    }

    enableKotlin = false
}

dependencies {
    api(project(":mockito-core"))
    implementation(files(repackagedDexmakerJar).builtBy(repackageDexmakerInline))
    // The dexmaker-mockito-inline AAR is repackaged above, only its base library is used as is.
    implementation(libs.dexmaker)
    add(dexmakerInline.name, libs.dexmaker.mockito.inline)
}
