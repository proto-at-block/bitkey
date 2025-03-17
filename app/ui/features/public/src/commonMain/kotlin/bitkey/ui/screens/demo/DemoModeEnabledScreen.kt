package bitkey.ui.screens.demo

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.ScreenModel
import build.wallet.ui.model.alert.DisableAlertModel
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.launch

data object DemoModeEnabledScreen : Screen

@BitkeyInject(ActivityScope::class, boundTypes = [DemoModeEnabledScreenPresenter::class])
class DemoModeEnabledScreenPresenter(
  private val accountConfigService: AccountConfigService,
) : ScreenPresenter<DemoModeEnabledScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: DemoModeEnabledScreen,
  ): ScreenModel {
    val scope = rememberStableCoroutineScope()
    var confirmingCancellation by remember { mutableStateOf(false) }
    return EnableDemoModeBodyModel(
      onBack = {
        navigator.exit()
      },
      switchIsChecked = true,
      onSwitchCheckedChange = { confirmingCancellation = true },
      disableAlertModel = when {
        confirmingCancellation -> {
          DisableAlertModel(
            title = "Disable Demo Mode?",
            subline = "You will be returned to the default settings once you hit “Disable”",
            onConfirm = {
              scope.launch {
                disableDemoMode()
                  .onSuccess {
                    navigator.goTo(DemoModeDisabledScreen)
                  }
                  .onFailure {
                    // TODO: show error
                    navigator.exit()
                  }
                confirmingCancellation = false
              }
            },
            onCancel = {
              confirmingCancellation = false
            }
          )
        }

        else -> null
      }
    ).asRootFullScreen()
  }

  // TODO: extract into service
  private suspend fun disableDemoMode(): Result<Unit, Error> =
    coroutineBinding {
      accountConfigService.setIsHardwareFake(value = false).bind()
      accountConfigService.setIsTestAccount(value = false).bind()
    }
}
