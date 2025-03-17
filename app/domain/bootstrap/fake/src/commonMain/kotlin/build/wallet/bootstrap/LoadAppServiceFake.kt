package build.wallet.bootstrap

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class LoadAppServiceFake : LoadAppService {
  val appState = MutableStateFlow<AppState?>(null)

  override suspend fun loadAppState(): AppState {
    return appState.filterNotNull().first()
  }

  fun reset() {
    appState.value = null
  }
}
