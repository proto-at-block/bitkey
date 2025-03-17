package build.wallet.codegen

import bitkey.ui.Snapshot
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import java.io.File

private const val COMPOSABLE_ANNOTATION = "androidx.compose.runtime.Composable"
private const val COMPOSE_MODEL = "build.wallet.ui.model.ComposeModel"

/**
 * Holds references to the various output locations including manual
 * directory references (i.e. locations outside the target module)
 * and the KSP code generator for in module targets.
 */
data class OutputDirectory(
  /** The output location for generated Swift (Snapshot-Testing) test case code. */
  val swiftTests: File,
  /** The output location for generated Kotlin (Paparazzi) test case code. */
  val kotlinTests: File,
  /** The output location for supplemental Kotlin code used in the Swift test cases. */
  val kotlinIos: File,
  /** The KSP module code generator only used to link generated code with sources files. */
  val codeGenerator: CodeGenerator,
)

/**
 * A KSP processor used to identify `@Snapshot` properties and generate
 * Kotlin and Swift test code for UI snapshots.
 */
class SnapshotGenerator(
  private val logger: KSPLogger,
  private val outputs: OutputDirectory,
) : SymbolProcessor {
  class Provider : SymbolProcessorProvider {
    private fun SymbolProcessorEnvironment.requireOption(name: String): String {
      return requireNotNull(options[name]) {
        "`snapshot-generator` requires ksp { arg(\"$name\", file) } in the associated build.gradle.kts"
      }
    }

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
      val swiftTestCaseOutputDirectory = environment.requireOption("swiftTestCaseOutputDirectory")
      val kotlinTestCaseOutputDirectory = environment.requireOption("kotlinTestCaseOutputDirectory")
      val kotlinIosOutputDirectory = environment.requireOption("kotlinIosOutputDirectory")
      return SnapshotGenerator(
        logger = environment.logger,
        outputs = OutputDirectory(
          swiftTests = File(swiftTestCaseOutputDirectory),
          kotlinTests = File(kotlinTestCaseOutputDirectory),
          kotlinIos = File(kotlinIosOutputDirectory),
          codeGenerator = environment.codeGenerator
        )
      )
    }
  }

  override fun process(resolver: Resolver): List<KSAnnotated> {
    val snapshotModels = resolver.getSymbolsWithAnnotation(Snapshot::class.qualifiedName!!)
      .filterIsInstance<KSPropertyDeclaration>()
    val composables = resolver.getSymbolsWithAnnotation(COMPOSABLE_ANNOTATION)
      .filterIsInstance<KSFunctionDeclaration>()
    val snapshotModelData = snapshotModels
      .mapNotNull { model ->
        mapSnapshotPropertyToData(model, composables)
      }

    val snapshotModelMap = snapshotModelData.groupBy {
      it.modelType.declaration.simpleName.asString()
    }
    if (snapshotModelMap.isNotEmpty()) {
      val generator = SnapshotCodegen(outputs = outputs)

      snapshotModelMap.forEach { (name, modelData) ->
        generator.generateTestCase(name, modelData)
      }

      generator.writeFiles()
    }
    return emptyList()
  }

  /**
   * Given a [model] which defines some kind of UI model
   * data class instance, attempt to identify the Composable
   * used to render the model.
   *
   * First check if the model is `ComposeModel` which can be rendered directly,
   * if that fails look for `@Composable` functions that accept the Model type.
   *
   * TODO: Remove support for Snapshotting non-ComposeModel presentation models.
   */
  private fun mapSnapshotPropertyToData(
    model: KSPropertyDeclaration,
    composables: Sequence<KSFunctionDeclaration>,
  ): SnapshotModelTestData? {
    val modelType = model.type.resolve()
    val modelClass = modelType.declaration as KSClassDeclaration
    val isComposableRendered = modelClass
      .getAllSuperTypes()
      .any { it.declaration.qualifiedName?.asString() == COMPOSE_MODEL }
    val composable = if (isComposableRendered) {
      modelClass.getDeclaredFunctions()
        .singleOrNull { it.simpleName.asString() == "render" }
    } else {
      composables.firstOrNull { composable ->
        composable.parameters.any { it.type.resolve() == modelType }
      }
    }

    return if (composable == null) {
      logger.error(
        "'${model.simpleName}' annotated with `@Snapshot` but could not find associated `@Composable` function.",
        model
      )
      null
    } else {
      SnapshotModelTestData(
        modelProperty = model,
        modelType = modelType,
        composable = composable
      )
    }
  }
}

data class SnapshotModelTestData(
  val modelProperty: KSPropertyDeclaration,
  val modelType: KSType,
  val composable: KSFunctionDeclaration,
)
