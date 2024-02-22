package build.wallet.gradle.logic.gradle

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible

/**
 * If [this] is a [Provider] or [ProviderConvertible], unwrap and return enclosing value.
 * Otherwise return [this].
 */
internal fun Any.unwrappedProvider(): Any =
  when (this) {
    is Provider<*> -> get()
    is ProviderConvertible<*> -> asProvider().get()
    else -> this
  }
