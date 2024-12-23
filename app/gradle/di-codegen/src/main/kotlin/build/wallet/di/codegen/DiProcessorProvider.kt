package build.wallet.di.codegen

import build.wallet.di.codegen.processor.BitkeyInjectSymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Provides our custom Kotlin symbol processors for DI code generation.
 */
class DiProcessorProvider : SymbolProcessorProvider {
  companion object {
    /**
     * The package in which code is generated that should be picked up during the merging phase.
     */
    internal const val LOOKUP_PACKAGE = "build.wallet.di"
  }

  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return BitkeyInjectSymbolProcessor(environment.codeGenerator, environment.logger)
  }
}
