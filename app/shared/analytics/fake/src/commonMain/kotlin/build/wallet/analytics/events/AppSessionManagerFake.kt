package build.wallet.analytics.events

import kotlinx.coroutines.flow.MutableStateFlow

class AppSessionManagerFake(
  private val sessionId: String = "session-id",
) : AppSessionManager {
  override val appSessionState: MutableStateFlow<AppSessionState> =
    MutableStateFlow(AppSessionState.FOREGROUND)

  override fun getSessionId(): String = sessionId

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
