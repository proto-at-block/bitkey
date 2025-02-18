@file:OptIn(ExperimentalUuidApi::class)

package build.wallet.platform.random

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Use this interface over [uuid] when you need to inject the dependency for testing purposes.
 */
fun interface UuidGenerator {
  /**
   * Generates a new random Universally Unique Identifier (UUID) and returns hexadecimal
   * representation.
   */
  fun random(): String
}

/**
 * Generates a new random Universally Unique Identifier (UUID) and returns the standard string
 * representation.
 *
 * The uuid is produced using a cryptographically secure pseudorandom number generator on all platforms.
 * See [https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.uuid/-uuid/-companion/random.html].
 *
 * Use this over [UuidGenerator] when you don't need to inject the dependency for
 * testing purposes.
 */
fun uuid(): String = Uuid.random().toString()
