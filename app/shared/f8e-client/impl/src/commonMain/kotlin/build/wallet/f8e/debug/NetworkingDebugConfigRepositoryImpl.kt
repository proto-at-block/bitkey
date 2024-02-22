package build.wallet.f8e.debug

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NetworkingDebugConfigRepositoryImpl(
  private val networkingDebugConfigDao: NetworkingDebugConfigDao,
) : NetworkingDebugConfigRepository {
  private val defaultConfig =
    NetworkingDebugConfig(
      failF8eRequests = false
    )

  private val configState = MutableStateFlow(defaultConfig)

  override val config: StateFlow<NetworkingDebugConfig>
    get() = configState.asStateFlow()

  override suspend fun setFailF8eRequests(value: Boolean): Result<Unit, Error> {
    return networkingDebugConfigDao.updateConfig { config ->
      config.copy(failF8eRequests = value)
    }
  }

  override fun launchSync(scope: CoroutineScope) {
    scope.launch {
      networkingDebugConfigDao.config()
        .map { it.get() }
        .filterNotNull()
        .collect(configState)
    }
  }
}
