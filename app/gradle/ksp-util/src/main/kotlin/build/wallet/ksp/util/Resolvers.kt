package build.wallet.ksp.util

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import kotlin.reflect.KClass

/**
 * Shortcut for getting symbols with a specific annotation type.
 */
fun Resolver.getSymbolsWithAnnotation(
  logger: KSPLogger,
  annotation: KClass<*>,
): Sequence<KSAnnotated> {
  val annotation = requireNotNull(annotation.qualifiedName) {
    val message = "Qualified name was null for $annotation"
    logger.error(message)
    message
  }
  return getSymbolsWithAnnotation(annotation)
}
