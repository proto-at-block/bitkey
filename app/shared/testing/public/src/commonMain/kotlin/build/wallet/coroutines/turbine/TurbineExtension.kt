package build.wallet.coroutines.turbine

import app.cash.turbine.Turbine
import build.wallet.kotest.extensionLazy
import io.kotest.core.TestConfiguration
import io.kotest.core.extensions.Extension

/**
 * Allows creating [Turbine] instances for tests without needed to manually assert that all events
 * have been consumed at the end of each test (using [Turbine.expectNoEvents]).
 *
 * Usage example:
 * ```kotlin
 * class SomeTests : FunSpec({
 *   val someTurbine = turbines.create<Int>("some events")
 *
 *   test("test this") {
 *     someTurbine += 0
 *     someTurbine += 1
 *
 *     someTurbine.awaitItem().shouldBe(0)
 *     // Test fails because `1` was not consumed.
 *   }
 * })
 * ```
 */
val TestConfiguration.turbines: TurbineExtension
  get() =
    extensionLazy {
      TurbineExtensionImpl(
        turbinesMap = TurbinesMap()
      )
    }

/**
 * Kotest extension for creating [Turbine] instances. At the end of the test, the extension asserts
 * that all events in the turbines have been consumed ([Turbine.expectNoEvents]).
 *
 * Use with [TestConfiguration.turbines].
 */
interface TurbineExtension : Extension {
  fun <T> create(name: String): Turbine<T>

  /**
   * Remove a turbine with matching name predicate, if any.
   */
  fun removeTurbine(namePredicate: (String) -> Boolean)
}
