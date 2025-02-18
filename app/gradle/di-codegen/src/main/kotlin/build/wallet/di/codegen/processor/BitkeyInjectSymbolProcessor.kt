package build.wallet.di.codegen.processor

import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import build.wallet.di.SingleIn
import build.wallet.di.codegen.DiProcessorProvider.Companion.LOOKUP_PACKAGE
import build.wallet.di.codegen.processor.util.addContributesToAnnotation
import build.wallet.di.codegen.processor.util.addOriginAnnotation
import build.wallet.di.codegen.processor.util.addSingleInAnnotation
import build.wallet.ksp.util.*
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * Processor that generates DI code for [BitkeyInject] annotation.
 *
 * In the lookup package [LOOKUP_PACKAGE] a new component interface is generated with provider
 * methods for the annotated type, that binds the implementation to the bound type(s) as a singleton
 * in the specified scope. Has similar effect as combination of [SingleIn] and [ContributesBinding]
 * with a few exceptions, see [BitkeyInject] docs for more details.
 *
 * Example:
 * ```
 * package build.wallet.test
 *
 * @BitkeyInject(AppScope::class)
 * class BaseImpl : Base1, Base2
 * ```
 *
 * Will generate Anvil component:
 * ```
 * package $LOOKUP_PACKAGE
 *
 * import build.wallet.test.Base1
 * import build.wallet.test.Base2
 * import build.wallet.test.BaseImpl
 * import me.tatarka.inject.annotations.Provides
 * import build.wallet.di.AppScope
 * import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
 * import build.wallet.di.SingleIn
 * import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin
 *
 * @Origin(value = BaseImpl::class)
 * @ContributesTo(scope = AppScope::class)
 * public interface AccountServiceImplComponent {
 *    @Provides
 *    @SingleIn(scope = AppScope::class)
 *    public fun provideBaseImpl(): BaseImpl = BaseImpl()
 *
 *    @Provides
 *    @SingleIn(scope = AppScope::class)
 *    public fun provideBase1(implementation: BaseImpl): Base1 = implementation
 *
 *    @Provides
 *    @SingleIn(scope = AppScope::class)
 *    public fun provideBase2(implementation: BaseImpl): Base2 = implementation
 * }
 * ```
 */
internal class BitkeyInjectSymbolProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
) : SymbolProcessor {
  override fun process(resolver: Resolver): List<KSAnnotated> {
    resolver
      .getSymbolsWithAnnotation(logger, BitkeyInject::class)
      .filterIsInstance<KSClassDeclaration>()
      .forEach { clazz ->
        generateComponentInterface(clazz)
      }

    return emptyList()
  }

  /**
   * Generates a kotlin-inject-anvil component interface for the annotated class.
   *
   * For example, fore code:
   * ```kotlin
   * @Impl
   * @BitkeyInject(AppScope::class)
   * class NfcCommandsImpl(
   *   private val clock: Clock,
   * ) : NfcCommands { ... }
   * ```
   *
   * Will generate:
   *
   * ```kotlin
   * @Origin(value = NfcCommandsImpl::class)
   * @ContributesTo(scope = AppScope::class)
   * public interface NfcCommandsImplComponent {
   *   @Provides
   *   @SingleIn(scope = AppScope::class)
   *   public fun provideNfcCommandsImpl(clock: Clock): NfcCommandsImpl = NfcCommandsImpl(clock)
   *
   *   @Provides
   *   public fun provideNfcCommandsAsImpl(implementation: NfcCommandsImpl): @Impl NfcCommands = implementation
   * }
   * ```
   */
  private fun generateComponentInterface(clazz: KSClassDeclaration) {
    val annotation = clazz.getContributesBindingAnnotation()
    val scope = annotation.getScope()

    val className = clazz.simpleName.asString()
    val componentName = ClassName(LOOKUP_PACKAGE, "${className}Component")
    val componentFileSpec = FileSpec.builder(componentName)
      .addType(
        TypeSpec.interfaceBuilder(componentName)
          .addOriginatingKSFile(clazz.containingFile!!)
          .addOriginAnnotation(clazz)
          .addContributesToAnnotation(scope)
          .addProvideFunctions(clazz, scope, annotation)
          .build()
      )
      .build()

    // Write generated component file.
    componentFileSpec.writeTo(codeGenerator, aggregating = false)
  }

  /**
   * Adds provide method to a component, for each bound type:
   *
   * ```kotlin
   * @Provides
   * @SingleIn(AppScope::class)
   * fun provideFoo1(implementation: FooImpl): Foo1 = implementation
   *
   * @Provides
   * @SingleIn(AppScope::class)
   * fun provideFoo1(impl: FooImpl): Foo1 = impl
   * ```
   */
  private fun TypeSpec.Builder.addProvideFunctions(
    impl: KSClassDeclaration,
    scope: KSType,
    annotation: KSAnnotation,
  ): TypeSpec.Builder =
    apply {
      // @Impl or @Fake, if any.
      val implementationQualifier = impl.getImplementationQualifier()
      val directSuperTypes = impl.getDirectSuperTypes()
      val explicitBoundTypes = annotation.getBoundTypes()
      // If no explicit bound types are specified, use direct supertypes of the implementation.
      val boundTypesToUse = explicitBoundTypes.ifEmpty { directSuperTypes }
      addImplProvider(impl, scope)

      // If the bound type is the only and the impl type itself, no need to add binding provider.
      val isBoundTypeSameAsImpl = boundTypesToUse.singleOrNull()?.let { boundType ->
        impl.asType(emptyList()).isAssignableFrom(boundType)
      } == true

      if (!isBoundTypeSameAsImpl) {
        boundTypesToUse.forEach { boundType ->
          addBindingProvider(impl, boundType, implementationQualifier)
        }
      }
    }

  /**
   * Creates a binding provider for the implementation class.
   *
   * For code:
   * ```kotlin
   * @Impl
   * @BitkeyInject(AppScope::class)
   * class NfcCommandsImpl(
   *   private val clock: Clock,
   * ) : NfcCommands { ... }
   * ```
   * ```
   *
   * Will generate:
   * ```kotlin
   * @Provides
   * public fun provideNfcCommandsAsImpl(implementation: NfcCommandsImpl): @Impl NfcCommands = implementation
   * ```
   *
   * @param impl implementation class (example `FooImpl`)
   * @param boundType what type the implementation should be bound to, example (`Foo`)
   * @param implementationQualifier qualifier annotation, if any (`@Impl` or `@Fake`), to which
   * the implementation should be bound.
   */
  private fun TypeSpec.Builder.addBindingProvider(
    impl: KSClassDeclaration,
    boundType: KSType,
    implementationQualifier: KSAnnotation?,
  ): TypeSpec.Builder {
    // "Impl" or "Fake", if any.
    val qualifierName = implementationQualifier?.toClassName()?.simpleName

    // Add qualifier postfix to the function name, if any - "AsImpl" or "AsFake".
    val qualifierPostfix = qualifierName?.let { "As$it" }.orEmpty()
    return addFunction(
      FunSpec
        // Provide method name is `provideFooAsImpl` or `provideFooAsFake` if qualifier is present.
        // Otherwise, it's just `provideFoo`.
        .builder("provide${boundType.toClassName().simpleName}$qualifierPostfix")
        .addParameter(
          name = "implementation",
          type = impl.toClassName()
        )
        .addAnnotation(Provides::class)
        // Add `@Impl` or `@Fake` annotation, if any.
        .apply {
          if (implementationQualifier != null) {
            addAnnotation(implementationQualifier.toClassName())
          }
        }
        // Return type
        .returns(boundType.toTypeName())
        .addCode("return implementation")
        .build()
    )
  }

  /**
   * Adds implementation provider to
   */
  private fun TypeSpec.Builder.addImplProvider(
    impl: KSClassDeclaration,
    scope: KSType,
  ): TypeSpec.Builder {
    val className = impl.toClassName()

    // Extract constructor parameters
    val constructorParams = impl.primaryConstructor
      ?.parameters
      .orEmpty()
      .filter { !it.hasDefault }

    val funSpecBuilder = FunSpec.builder("provide${className.simpleName}")
      .addAnnotation(Provides::class)
      .addSingleInAnnotation(scope)
      .returns(className)

    // Add parameters to the function
    constructorParams.forEach { param ->
      val paramName = param.name?.asString()
      val paramType = param.type.resolve().toTypeName()

      if (paramName != null) {
        val paramBuilder = ParameterSpec.builder(paramName, paramType)

        // Maintain annotations on the parameter, like @Impl or @Fake qualifiers.
        param.annotations.forEach { annotation ->
          paramBuilder.addAnnotation(annotation.toClassName())
        }

        funSpecBuilder.addParameter(paramBuilder.build())
      }
    }

    // Generate constructor call
    val constructorArguments = constructorParams
      .joinToString(", ") { it.name!!.asString() }
    funSpecBuilder.addCode("return %T($constructorArguments)", className)

    return addFunction(funSpecBuilder.build())
  }

  /**
   * Get direct types implemented by the class declaration. Currently, only interfaces and abstract
   * classes are considered.
   */
  private fun KSClassDeclaration.getDirectSuperTypes(): Set<KSType> =
    superTypes
      .map { it.resolve() }
      .filter { it.isInterface || it.isAbstractClass }
      .toSet()

  private fun KSClassDeclaration.getContributesBindingAnnotation(): KSAnnotation {
    return annotations
      .find { it.isOfType<BitkeyInject>() }
      ?: run {
        val message = "Missing @BitkeyInject annotation on ${simpleName.asString()}"
        logger.error(message, this)
        error(message)
      }
  }

  /**
   * Get an implementation qualifier (`@Impl` or `@Fake`) for the class declaration, if any.
   */
  private fun KSClassDeclaration.getImplementationQualifier(): KSAnnotation? {
    val implQualifier = annotations.find { it.isOfType<Impl>() }
    val fakeQualifier = annotations.find { it.isOfType<Fake>() }
    return when {
      implQualifier != null && fakeQualifier != null -> {
        val message = "Can't have both @Impl and @Fake qualifiers on ${simpleName.asString()}"
        logger.error(message, this)
        error(message)
      }
      else -> implQualifier ?: fakeQualifier
    }
  }

  private fun KSAnnotation.getScope(): KSType {
    return arguments
      .first { it.name!!.asString() == "scope" }.value as KSType
  }

  /**
   * Returns list of bound types from [BitkeyInject.boundTypes].
   * If empty, supertypes of the class, if any, will be used for binding implementation.
   */
  private fun KSAnnotation.getBoundTypes(): Set<KSType> {
    @Suppress("UNCHECKED_CAST")
    return (
      arguments
        .first { it.name?.asString() == "boundTypes" }
        .value as? Collection<KSType>
    )?.toSet().orEmpty()
  }
}
