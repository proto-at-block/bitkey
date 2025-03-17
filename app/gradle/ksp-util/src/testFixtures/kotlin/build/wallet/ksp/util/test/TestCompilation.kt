@file:OptIn(ExperimentalCompilerApi::class)

package build.wallet.ksp.util.test

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File
import java.nio.file.Files
import java.util.*

/**
 * Creates a new Kotlin compilation with the given sources and KSP symbol processors for
 * testing purposes.
 *
 * @param sources Kotlin source code contents to compile.
 */
fun compilation(
  vararg sources: String,
  iosSources: Boolean = false,
): KotlinCompilation {
  return KotlinCompilation().apply {
    allWarningsAsErrors = true
    messageOutputStream = System.out
    inheritClassPath = true

    if (iosSources) {
      optIn = listOf("kotlin.experimental.ExperimentalObjCRefinement")
    }

    addSources(*sources, iosSources = iosSources)

    configureKsp(useKsp2 = true) {
      // Add all symbol processor providers found in the classpath
      symbolProcessorProviders += ServiceLoader.load(
        SymbolProcessorProvider::class.java,
        SymbolProcessorProvider::class.java.classLoader
      )
      // Run KSP embedded directly within this kotlinc invocation
      withCompilation = true
      incremental = true
    }
  }
}

/**
 * Adds the given sources to this compilation with their packages and names inferred.
 */
private fun KotlinCompilation.addSources(
  @Language("kotlin") vararg sources: String,
  iosSources: Boolean,
): KotlinCompilation =
  apply {
    this.sources += sources.mapIndexed { index, content ->
      val packageDir = content.lines()
        .firstOrNull { it.trim().startsWith("package ") }
        ?.substringAfter("package ")
        ?.replace('.', '/')
        ?.let { "$it/" }
        ?: ""

      val target = if (iosSources) "iosMain/kotlin" else "main/java"
      val name = "${workingDir.absolutePath}/sources/src/$target/" +
        "$packageDir/Source$index.kt"

      Files.createDirectories(File(name).parentFile.toPath())

      SourceFile.kotlin(name, contents = content, trimIndent = true)
    }
  }

fun KotlinCompilation.getKspGeneratedFiles(): List<File> {
  return kspSourcesDir.walk()
    .filter { it.name.endsWith(".kt") }
    .toList()
}
