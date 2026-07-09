package bitkey.ui.screens.securityhub

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState.BACKGROUND
import build.wallet.platform.app.AppSessionState.FOREGROUND
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Tracks the Security Hub all-set pill lifecycle for a foreground session.
 *
 * Expected behavior:
 * - The all-set pill starts visible the first time Security Hub renders in a foreground session.
 * - After the entry delay, Security Hub auto-scrolls once per foreground session to tuck the pill
 *   behind the settings area.
 * - A deliberate downward pull reveals the tucked pill again, with a selection haptic when the
 *   pill is fully revealed.
 * - Backgrounding the app starts a fresh session so the pill can show and auto-hide again.
 */
interface SecurityHubAllSetPillSessionManager {
  val hasAutoHiddenPillInCurrentForegroundSession: StateFlow<Boolean>
  val foregroundSessionGeneration: StateFlow<Int>
  val isAppForegrounded: StateFlow<Boolean>

  fun markPillAutoHidden(foregroundSessionGeneration: Int): Boolean
}

@BitkeyInject(AppScope::class)
class SecurityHubAllSetPillSessionManagerImpl(
  private val appSessionManager: AppSessionManager,
  private val appScope: CoroutineScope,
) : SecurityHubAllSetPillSessionManager {
  private val hasAutoHiddenPill = MutableStateFlow(false)
  private val foregroundSessionGenerationFlow = MutableStateFlow(0)
  private val isAppForegroundedFlow = MutableStateFlow(appSessionManager.isAppForegrounded())

  override val hasAutoHiddenPillInCurrentForegroundSession: StateFlow<Boolean> = hasAutoHiddenPill
  override val foregroundSessionGeneration: StateFlow<Int> = foregroundSessionGenerationFlow
  override val isAppForegrounded: StateFlow<Boolean> = isAppForegroundedFlow

  init {
    appScope.launch {
      appSessionManager.appSessionState.collect { sessionState ->
        isAppForegroundedFlow.value = sessionState == FOREGROUND
        if (sessionState == BACKGROUND) {
          hasAutoHiddenPill.value = false
          foregroundSessionGenerationFlow.value += 1
        }
      }
    }
  }

  override fun markPillAutoHidden(foregroundSessionGeneration: Int): Boolean {
    if (
      foregroundSessionGenerationFlow.value == foregroundSessionGeneration &&
      appSessionManager.isAppForegrounded()
    ) {
      hasAutoHiddenPill.value = true
      return true
    }

    return false
  }
}
