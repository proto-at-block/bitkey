package build.wallet.platform.app

import kotlinx.coroutines.flow.MutableStateFlow

class AppSessionManagerFake(
  sessionId: String = "session-id",
) : AppSessionManager {
  var currentSessionId: String = sessionId

  override val appSessionState: MutableStateFlow<AppSessionState> =
    MutableStateFlow(AppSessionState.FOREGROUND)

  override fun getSessionId(): String = currentSessionId

  override fun appDidEnterBackground() {
    appSessionState.value = AppSessionState.BACKGROUND
  }

  override fun appDidEnterForeground() {
    appSessionState.value = AppSessionState.FOREGROUND
  }

  override fun isAppForegrounded(): Boolean {
    return appSessionState.value == AppSessionState.FOREGROUND
  }

  fun reset() {
    appSessionState.value = AppSessionState.FOREGROUND
  }
}
