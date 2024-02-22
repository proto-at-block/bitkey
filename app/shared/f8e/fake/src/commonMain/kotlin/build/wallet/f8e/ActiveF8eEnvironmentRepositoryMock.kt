package build.wallet.f8e

import app.cash.turbine.Turbine
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ActiveF8eEnvironmentRepositoryMock(
  turbine: ((String) -> Turbine<Any>),
  f8eEnvironment: Result<F8eEnvironment?, Error> = Ok(F8eEnvironment.Production),
) : ActiveF8eEnvironmentRepository {
  var activeF8eEnvironmentValue = MutableStateFlow(f8eEnvironment)

  val activeF8eEnvironmentCalls = turbine.invoke("get active f8eEnvironment calls")

  override fun activeF8eEnvironment(): Flow<Result<F8eEnvironment?, Error>> {
    activeF8eEnvironmentCalls?.add(Unit)
    return activeF8eEnvironmentValue
  }
}
