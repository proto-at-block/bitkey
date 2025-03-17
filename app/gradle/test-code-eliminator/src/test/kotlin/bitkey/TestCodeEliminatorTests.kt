package bitkey

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCompilerApi::class)
class TestCodeEliminatorTests {
  @Test
  fun test_compilation_fails_on_usage_of_eliminated_code() {
    val compilation = KotlinCompilation().apply {
      allWarningsAsErrors = true
      messageOutputStream = System.out
      inheritClassPath = true
      compilerPluginRegistrars += TestCodeEliminatorCompilerPluginRegistrar()
      sources += SourceFile.kotlin(
        "annotation.kt",
        """
        package bitkey.ui

        annotation class Snapshot
        object Snapshots
        """
      )

      sources += SourceFile.kotlin(
        "subject.kt",
        """
        data class MyModel(val mydata: String = "Hello")
        
        @bitkey.ui.Snapshot
        val bitkey.ui.Snapshots.testSnapshot
            get() = MyModel(mydata = "World")
        """.trimIndent()
      )

      sources += SourceFile.kotlin(
        "usesite.kt",
        """
        fun main() {
            println(bitkey.ui.Snapshots.testSnapshot)
        }
        """.trimIndent()
      )
    }

    val result = compilation.compile()

    // Fails with low-level IR error because references to
    // eliminated code are not resolvable.
    assertEquals(KotlinCompilation.ExitCode.INTERNAL_ERROR, result.exitCode)
  }

  @Test
  fun test_unrelated_annotations_do_not_eliminate_code() {
    val compilation = KotlinCompilation().apply {
      allWarningsAsErrors = true
      messageOutputStream = System.out
      inheritClassPath = true
      compilerPluginRegistrars += TestCodeEliminatorCompilerPluginRegistrar()
      sources += SourceFile.kotlin(
        "annotation.kt",
        """
        package totally.different.packageName

        annotation class Snapshot
        object Snapshots
        """
      )

      sources += SourceFile.kotlin(
        "subject.kt",
        """
        import totally.different.packageName.*
        
        data class MyModel(val mydata: String = "Hello")
        
        @Snapshot
        val Snapshots.testSnapshot
            get() = MyModel(mydata = "World")
        """.trimIndent()
      )

      sources += SourceFile.kotlin(
        "usesite.kt",
        """
        import totally.different.packageName.*
        
        fun main() {
            println(Snapshots.testSnapshot)
        }
        """
      )
    }

    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
  }
}
