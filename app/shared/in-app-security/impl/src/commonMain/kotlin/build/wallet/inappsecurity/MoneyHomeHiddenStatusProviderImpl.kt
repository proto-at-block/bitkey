package build.wallet.inappsecurity

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@BitkeyInject(AppScope::class)
class MoneyHomeHiddenStatusProviderImpl(
  appSessionManager: AppSessionManager,
  appCoroutineScope: CoroutineScope,
  private val hideBalancePreference: HideBalancePreference,
) : MoneyHomeHiddenStatusProvider {
  // this is provided lazily to ensure that the preference is loaded properly before accessing
  override val hiddenStatus by lazy(LazyThreadSafetyMode.NONE) {
    if (hideBalancePreference.isEnabled.value) {
      MutableStateFlow(MoneyHomeHiddenStatus.HIDDEN)
    } else {
      MutableStateFlow(MoneyHomeHiddenStatus.VISIBLE)
    }
  }

  init {
    appSessionManager.appSessionState
      .onEach { sessionState ->
        // If the app is in the background and the hide balance preference is enabled,
        // hide the balance
        if (sessionState == AppSessionState.BACKGROUND && hideBalancePreference.isEnabled.value) {
          hiddenStatus.value = MoneyHomeHiddenStatus.HIDDEN
        }
      }
      .launchIn(appCoroutineScope)

    hideBalancePreference.isEnabled
      .onEach { enabled ->
        // If the hide balance preference is enabled, immediately hide the balance
        if (enabled) {
          hiddenStatus.value = MoneyHomeHiddenStatus.HIDDEN
        }
      }
      .launchIn(appCoroutineScope)
  }

  override fun toggleStatus() {
    hiddenStatus.value = if (hiddenStatus.value == MoneyHomeHiddenStatus.HIDDEN) {
      MoneyHomeHiddenStatus.VISIBLE
    } else {
      MoneyHomeHiddenStatus.HIDDEN
    }
  }
}
