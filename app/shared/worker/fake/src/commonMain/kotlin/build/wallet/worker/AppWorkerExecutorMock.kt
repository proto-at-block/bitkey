package build.wallet.worker

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign

class AppWorkerExecutorMock(
  turbine: (String) -> Turbine<Any>,
) : AppWorkerExecutor {
  val executeAllCalls = turbine("executeAll")

  override suspend fun executeAll() {
    executeAllCalls += Unit
  }
}
