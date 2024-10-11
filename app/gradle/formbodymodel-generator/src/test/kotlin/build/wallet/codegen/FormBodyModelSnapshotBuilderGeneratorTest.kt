package build.wallet.codegen

import com.tschuchort.compiletesting.*
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalCompilerApi::class)
class FormBodyModelSnapshotBuilderGeneratorTest {
  private val formBodyModelSource = SourceFile.kotlin(
    "FormBodyModel.kt",
    """
        package build.wallet.statemachine.core.form

        interface ScreenId
        // Recreate a simple FormBodyModel representation
        abstract class FormBodyModel(
            open val id: ScreenId,
            open val onBack: (() -> Unit)?,
        )
      """
  )

  @Test
  fun test_CodeGen() {
    val source = SourceFile.kotlin(
      "test.kt",
      """
        package build.wallet.statemachine.test
        
        import build.wallet.statemachine.core.form.FormBodyModel
        import build.wallet.statemachine.core.form.ScreenId

        // Implement FormBodyModel for a class requiring code generation
        data class MyCustomFormBodyModel(
            override val id: ScreenId,
            override val onBack: () -> Unit,
            val someCustomField: String
        ) : FormBodyModel(
            id = id,
            onBack = onBack,
        )
      """
    )

    val compilation = KotlinCompilation().apply {
      sources = listOf(formBodyModelSource, source)
      configureKsp(useKsp2 = true) {
        symbolProcessorProviders.add(FormBodyModelSnapshotBuilderGenerator.Provider())
      }
    }
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val kotlinFiles = compilation.getKspGeneratedFiles()
    val generatedFile = assertNotNull(kotlinFiles.firstOrNull())
    assertEquals("SnapshotTestModels.kt", generatedFile.name)

    assertKotlinEquals(
      """
        package build.wallet.statemachine
        
        import build.wallet.statemachine.core.form.FormBodyModel
        import build.wallet.statemachine.core.form.ScreenId
        import build.wallet.statemachine.test.MyCustomFormBodyModel
        import kotlin.String
        import kotlin.Unit
        
        public object SnapshotTestModels {
          public fun CreateMyCustomFormBodyModel(
            id: ScreenId,
            onBack: () -> Unit,
            someCustomField: String,
          ): FormBodyModel = MyCustomFormBodyModel(id = id,onBack = onBack,someCustomField =
              someCustomField,)
        }
      """,
      generatedFile.readText()
    )
  }

  @Test
  fun testCodeGen_Ignores_FormBodyModelImpl() {
    val source = SourceFile.kotlin(
      "test.kt",
      """
        package build.wallet.statemachine.test
        
        import build.wallet.statemachine.core.form.FormBodyModel
        import build.wallet.statemachine.core.form.ScreenId
        
        // Recreate FormBodyModelImpl which should be ignored
        data class FormBodyModelImpl(
            override val id: ScreenId,
            override val onBack: () -> Unit,
        ) : FormBodyModel(
            id = id,
            onBack = onBack,
        )
      
      """
    )

    val compilation = KotlinCompilation().apply {
      sources = listOf(formBodyModelSource, source)
      configureKsp(useKsp2 = true) {
        symbolProcessorProviders.add(FormBodyModelSnapshotBuilderGenerator.Provider())
      }
    }
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    assertTrue(compilation.getKspGeneratedFiles().isEmpty())
  }

  private fun KotlinCompilation.getKspGeneratedFiles(): List<File> {
    return kspSourcesDir.walk()
      .filter { it.name.endsWith(".kt") }
      .toList()
  }

  @Suppress("SameParameterValue")
  private fun assertKotlinEquals(
    @Language("kotlin") expected: String,
    @Language("kotlin") actual: String,
  ) {
    assertEquals(expected.trimIndent() + "\n", actual)
  }
}
