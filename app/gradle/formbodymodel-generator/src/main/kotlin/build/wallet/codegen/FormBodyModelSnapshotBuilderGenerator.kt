package build.wallet.codegen

import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

private const val SNAPSHOT_TEST_MODEL_NAME = "SnapshotTestModels"
private const val SNAPSHOT_TEST_MODEL_PACKAGE = "build.wallet.statemachine"
private const val FORM_BODY_MODEL_IMPL = "FormBodyModelImpl"
private const val FORM_BODY_MODEL_QUALIFIED_NAME =
  "build.wallet.statemachine.core.form.FormBodyModel"

class FormBodyModelSnapshotBuilderGenerator(
  private val codeGenerator: CodeGenerator,
) : SymbolProcessor {
  class Provider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
      return FormBodyModelSnapshotBuilderGenerator(environment.codeGenerator)
    }
  }

  private var completed = false

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (completed) {
      // file already generated for this compilation, skip.
      return emptyList()
    }

    val symbols = resolver.getAllFiles().findFormBodyModelClasses()

    if (symbols.isEmpty()) {
      // nothing to do
      return emptyList()
    }

    val formBodyModelTypeName = symbols.first().superTypes.first().toTypeName()

    val factoryFunctions = symbols.map { classDeclaration ->
      val classTypeName = classDeclaration.asType(emptyList()).toTypeName()
      val params = classDeclaration.primaryConstructor!!.parameters
      val returnStatement = buildString {
        append("return %T(")
        params.forEach { param ->
          val paramName = param.name!!.asString()
          append(paramName)
          append(" = ")
          append(paramName)
          append(",")
        }
        append(")")
      }
      FunSpec.builder("Create${classDeclaration.simpleName.asString()}")
        .addParameters(
          params.map { param ->
            val paramName = param.name!!.asString()
            val paramType = param.type.toTypeName()
            ParameterSpec.builder(paramName, paramType).build()
          }
        )
        .addCode(
          CodeBlock.builder()
            .addStatement(returnStatement, classTypeName)
            .build()
        )
        .returns(formBodyModelTypeName)
        .build()
    }

    val generatedFile = FileSpec
      .builder(SNAPSHOT_TEST_MODEL_PACKAGE, SNAPSHOT_TEST_MODEL_NAME)
      .addType(
        TypeSpec.Companion.objectBuilder(SNAPSHOT_TEST_MODEL_NAME)
          .addFunctions(factoryFunctions)
          .build()
      )
      .build()

    generatedFile.writeTo(codeGenerator, aggregating = true)
    completed = true
    return emptyList()
  }

  /**
   * Select all class declarations from the provided files that implement FormBodyModel.
   */
  private fun Sequence<KSFile>.findFormBodyModelClasses(): List<KSClassDeclaration> {
    return flatMap { file ->
      // Special handling for RotateAuthKeyScreens which contains models within an object
      if (file.fileName == "RotateAuthKeyScreens.kt") {
        (file.declarations.first() as KSClassDeclaration)
          .declarations
          .filterFormBodyModelClasses()
      } else {
        file.declarations.filterFormBodyModelClasses()
      }
    }.toList()
  }

  private fun Sequence<KSDeclaration>.filterFormBodyModelClasses(): Sequence<KSClassDeclaration> {
    return filterIsInstance<KSClassDeclaration>()
      .filter { declaration ->
        !declaration.isInternal() &&
          !declaration.isPrivate() &&
          declaration.simpleName.asString() != FORM_BODY_MODEL_IMPL &&
          declaration.superTypes.any { superType ->
            superType.resolve()
              .declaration
              .qualifiedName
              ?.asString() == FORM_BODY_MODEL_QUALIFIED_NAME
          }
      }
  }
}
