package build.wallet.f8e.debug

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class NetworkingDebugServiceFake : NetworkingDebugService {
  val configState =
    MutableStateFlow(
      NetworkingDebugConfig(
        failF8eRequests = false
      )
    )
  override val config: StateFlow<NetworkingDebugConfig> = configState

  override suspend fun setFailF8eRequests(value: Boolean): Result<Unit, Error> {
    configState.update {
      it.copy(failF8eRequests = value)
    }
    return Ok(Unit)
  }

  override suspend fun launchSync() {
    // noop
  }
}
