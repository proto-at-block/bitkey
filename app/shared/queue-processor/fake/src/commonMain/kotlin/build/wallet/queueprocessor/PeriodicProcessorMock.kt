package build.wallet.queueprocessor

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign

class PeriodicProcessorMock(
  identifier: String,
  turbine: (String) -> Turbine<Any>,
) : PeriodicProcessor {
  val startCalls = turbine("{$identifier} start calls")

  override suspend fun start() {
    startCalls += Unit
  }
}
