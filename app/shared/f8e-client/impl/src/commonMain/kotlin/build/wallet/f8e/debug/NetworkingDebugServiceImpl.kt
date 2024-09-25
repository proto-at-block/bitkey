package build.wallet.f8e.debug

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class NetworkingDebugServiceImpl(
  private val networkingDebugConfigDao: NetworkingDebugConfigDao,
) : NetworkingDebugService {
  private val defaultConfig =
    NetworkingDebugConfig(
      failF8eRequests = false
    )

  private val configState = MutableStateFlow(defaultConfig)

  override val config: StateFlow<NetworkingDebugConfig> = configState.asStateFlow()

  override suspend fun setFailF8eRequests(value: Boolean): Result<Unit, Error> {
    return networkingDebugConfigDao.updateConfig { config ->
      config.copy(failF8eRequests = value)
    }
  }

  override suspend fun launchSync() {
    networkingDebugConfigDao.config()
      .map { it.get() }
      .filterNotNull()
      .collect(configState)
  }
}
