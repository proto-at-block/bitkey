package build.wallet.inappsecurity

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.inappsecurity.MoneyHomeHiddenStatus.HIDDEN
import build.wallet.inappsecurity.MoneyHomeHiddenStatus.VISIBLE
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState.BACKGROUND
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
class MoneyHomeHiddenStatusProviderImpl(
  appSessionManager: AppSessionManager,
  appCoroutineScope: CoroutineScope,
  hideBalancePreference: HideBalancePreference,
) : MoneyHomeHiddenStatusProvider {
  private val inBackground = appSessionManager.appSessionState.map { it == BACKGROUND }
  private val preferenceEnabled = hideBalancePreference.isEnabled

  // Assume hidden status by default while we are loading
  override val hiddenStatus = MutableStateFlow<MoneyHomeHiddenStatus>(HIDDEN)

  init {
    appCoroutineScope.launch {
      // Initialize based on current preference
      hiddenStatus.value = if (preferenceEnabled.first()) HIDDEN else VISIBLE

      launch {
        // If the hide balance preference becomes enabled, immediately hide the balance.
        preferenceEnabled.collectLatest { enabled ->
          if (enabled) {
            hiddenStatus.value = HIDDEN
          }
        }
      }

      launch {
        // If the app goes in background and preference is enabled, hide balance
        inBackground.collectLatest { inBackground ->
          if (inBackground && preferenceEnabled.first()) {
            hiddenStatus.value = HIDDEN
          }
        }
      }
    }
  }

  override fun toggleStatus() {
    hiddenStatus.update {
      when (it) {
        HIDDEN -> VISIBLE
        VISIBLE -> HIDDEN
      }
    }
  }
}
