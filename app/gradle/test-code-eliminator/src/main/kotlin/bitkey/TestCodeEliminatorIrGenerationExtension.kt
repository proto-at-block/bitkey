package bitkey

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName

private val SNAPSHOT_ANNOTATION = FqName("bitkey.ui.Snapshot")

/**
 * The main entrypoint to register IR transformations in the Kotlin Compiler.
 */
class TestCodeEliminatorIrGenerationExtension : IrGenerationExtension {
  override fun generate(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
  ) {
    val transformer = TestCodeEliminatorTransformer(listOf(SNAPSHOT_ANNOTATION))
    moduleFragment.transform(transformer, null)
  }
}

/**
 * Processes all incoming [IrFile] compilation units and removes
 * declarations with any of the provided [annotationTargets].
 *
 * Currently only supports removing [IrProperty] declarations.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
private class TestCodeEliminatorTransformer(
  private val annotationTargets: List<FqName>,
) : IrElementTransformerVoid() {
  override fun visitFile(declaration: IrFile): IrFile {
    declaration.declarations.removeIf {
      (it as? IrProperty)
        ?.let { annotationTargets.any(it::hasAnnotation) } == true
    }
    return declaration
  }
}
