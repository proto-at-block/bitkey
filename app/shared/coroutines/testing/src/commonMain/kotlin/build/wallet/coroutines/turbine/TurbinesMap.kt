package build.wallet.coroutines.turbine

import app.cash.turbine.Turbine

/**
 * A collection of named [Turbine]s. Used by [TurbineExtension].
 */
internal class TurbinesMap {
  private val turbines = mutableMapOf<String, Turbine<*>>()

  fun <T> create(name: String): Turbine<T> {
    require(name !in turbines) {
      "Turbine named $name already created"
    }

    return Turbine<T>(name = name).also {
      turbines[name] = it
    }
  }

  /**
   * Assert that all turbines have been fully consumed.
   */
  fun assertEmpty() {
    val failures =
      turbines.mapNotNull { (_, turbine) ->
        runCatching { turbine.ensureAllEventsConsumed() }.exceptionOrNull()?.message
      }

    check(failures.isEmpty()) { failures.joinToString(separator = "\n") }
  }

  fun remove(namePredicate: (String) -> Boolean) {
    turbines.keys.filter(namePredicate).forEach { turbines.remove(it) }
  }
}
