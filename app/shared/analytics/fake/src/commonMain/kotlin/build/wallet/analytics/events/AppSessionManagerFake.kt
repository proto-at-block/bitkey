package build.wallet.analytics.events

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSessionManagerFake(
  private val sessionId: String = "session-id",
) : AppSessionManager {
  private val flow = MutableStateFlow(AppSessionState.FOREGROUND)

  override val appSessionState: StateFlow<AppSessionState>
    get() = flow.asStateFlow()

  override fun getSessionId(): String = sessionId

  override fun appDidEnterBackground() {
    flow.value = AppSessionState.BACKGROUND
  }

  override fun appDidEnterForeground() {
    flow.value = AppSessionState.FOREGROUND
  }

  override fun isAppForegrounded(): Boolean {
    return flow.value == AppSessionState.FOREGROUND
  }

  fun reset() {
    flow.value = AppSessionState.FOREGROUND
  }
}
