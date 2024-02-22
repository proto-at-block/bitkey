package build.wallet.coroutines.turbine

import app.cash.turbine.Turbine
import io.kotest.core.listeners.AfterTestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult

internal class TurbineExtensionImpl(
  val turbinesMap: TurbinesMap,
) : TurbineExtension, AfterTestListener {
  override fun <T> create(name: String): Turbine<T> = turbinesMap.create(name)

  override suspend fun afterTest(
    testCase: TestCase,
    result: TestResult,
  ) {
    turbinesMap.assertEmpty()
  }

  override fun removeTurbine(namePredicate: (String) -> Boolean) {
    turbinesMap.remove(namePredicate)
  }
}
