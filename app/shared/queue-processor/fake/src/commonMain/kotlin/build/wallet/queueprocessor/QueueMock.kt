package build.wallet.queueprocessor

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import com.github.michaelbull.result.Result

class QueueMock<T : Any>(
  turbine: (String) -> Turbine<Any>,
) : Queue<T> {
  val appendCalls = turbine("append calls")
  lateinit var appendReturnValues: List<Result<Unit, Error>>
  var appendReturnCalls = 0

  fun reset() {
    appendReturnCalls = 0
  }

  override suspend fun append(item: T): Result<Unit, Error> {
    appendCalls += item
    return appendReturnValues[appendReturnCalls++]
  }

  override suspend fun take(num: Int): Result<List<T>, Error> {
    TODO("Not yet implemented")
  }

  override suspend fun removeFirst(num: Int): Result<Unit, Error> {
    TODO("Not yet implemented")
  }

  override suspend fun moveToEnd(num: Int): Result<Unit, Error> {
    TODO("Not yet implemented")
  }
}
