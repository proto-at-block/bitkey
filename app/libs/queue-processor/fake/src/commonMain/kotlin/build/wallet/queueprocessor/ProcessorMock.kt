package build.wallet.queueprocessor

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import com.github.michaelbull.result.Result

class ProcessorMock<T>(
  turbine: (String) -> Turbine<Any>,
) : Processor<T> {
  val processBatchCalls = turbine("process calls")
  lateinit var processBatchReturnValues: List<Result<Unit, Error>>
  var processBatchReturnCalls = 0

  override suspend fun processBatch(batch: List<T>): Result<Unit, Error> {
    processBatchCalls += batch
    return processBatchReturnValues[processBatchReturnCalls++]
  }

  fun reset() {
    processBatchReturnCalls = 0
  }
}
