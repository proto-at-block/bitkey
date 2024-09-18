package build.wallet.statemachine.account.create

import androidx.compose.runtime.*
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.onboarding.CreateSoftwareWalletService
import build.wallet.statemachine.account.create.CreateSoftwareWalletUiStateMachineImpl.State.*
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiProps
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachine
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Success
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class CreateSoftwareWalletUiStateMachineImpl(
  private val createSoftwareWalletService: CreateSoftwareWalletService,
  private val notificationPreferencesSetupUiStateMachine:
    NotificationPreferencesSetupUiStateMachine,
) : CreateSoftwareWalletUiStateMachine {
  @Composable
  override fun model(props: CreateSoftwareWalletProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(CreatingAccount) }

    return when (val state = uiState) {
      is CreatingAccount -> {
        LaunchedEffect("create-account") {
          createSoftwareWalletService
            .createAccount()
            .onSuccess {
              uiState = SettingUpNotifications(it)
            }
            .onFailure {
              props.onExit()
            }
        }
        LoadingBodyModel(
          id = null,
          onBack = null,
          message = "Creating Software Wallet..."
        ).asRootScreen()
      }

      is SettingUpNotifications -> {
        notificationPreferencesSetupUiStateMachine.model(
          NotificationPreferencesSetupUiProps(
            accountId = state.account.accountId,
            accountConfig = state.account.config,
            source = NotificationPreferencesProps.Source.Onboarding,
            onComplete = { uiState = SoftwareWalletCreated(state.account) }
          )
        )
      }
      is SoftwareWalletCreated -> {
        LoadingSuccessBodyModel(
          message = "Software Wallet Created",
          state = Success,
          id = null
        ).asRootScreen()
      }
    }
  }

  private sealed interface State {
    data object CreatingAccount : State

    data class SoftwareWalletCreated(
      val account: SoftwareAccount,
    ) : State

    data class SettingUpNotifications(
      val account: SoftwareAccount,
    ) : State
  }
}
