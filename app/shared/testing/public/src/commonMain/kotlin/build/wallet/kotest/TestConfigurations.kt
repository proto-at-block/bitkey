package build.wallet.kotest

import io.kotest.core.TestConfiguration
import io.kotest.core.extensions.Extension

/**
 * Lazily creates and registers an extension of type [T], unless it's already registered (keyed by
 * type [T]).
 */
internal inline fun <reified T : Extension> TestConfiguration.extensionLazy(
  createExtension: () -> T,
): T {
  val existingExtension = registeredExtensions().filterIsInstance<T>().singleOrNull()
  return existingExtension ?: extension(createExtension())
}
