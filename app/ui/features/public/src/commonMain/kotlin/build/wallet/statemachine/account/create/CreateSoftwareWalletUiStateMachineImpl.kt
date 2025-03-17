package build.wallet.statemachine.account.create

import androidx.compose.runtime.*
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.onboarding.OnboardSoftwareAccountService
import build.wallet.statemachine.account.create.CreateSoftwareWalletUiStateMachineImpl.State.*
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiProps
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Success
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class CreateSoftwareWalletUiStateMachineImpl(
  private val onboardSoftwareAccountService: OnboardSoftwareAccountService,
  private val notificationPreferencesSetupUiStateMachine:
    NotificationPreferencesSetupUiStateMachine,
) : CreateSoftwareWalletUiStateMachine {
  @Composable
  override fun model(props: CreateSoftwareWalletProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(CreatingAccount) }

    return when (val state = uiState) {
      is CreatingAccount -> {
        LaunchedEffect("create-account") {
          onboardSoftwareAccountService
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

      is SettingUpNotifications ->
        notificationPreferencesSetupUiStateMachine.model(
          NotificationPreferencesSetupUiProps(
            accountId = state.account.accountId,
            source = NotificationPreferencesProps.Source.Onboarding,
            onComplete = { uiState = SoftwareWalletCreated(state.account) }
          )
        )

      is SoftwareWalletCreated -> {
        LaunchedEffect("software-wallet-created") {
          props.onSuccess(state.account)
        }
        LoadingSuccessBodyModel(
          message = "Software Wallet Created",
          state = Success,
          id = null
        ).asRootScreen()
      }
      is SoftwareWalletFailed -> ErrorFormBodyModel(
        eventTrackerScreenId = null,
        title = "Failed to create software account, error: ${state.throwable}",
        primaryButton = ButtonDataModel(
          text = "Ok",
          onClick = props.onExit
        )
      ).asRootScreen()
    }
  }

  private sealed interface State {
    data object CreatingAccount : State

    data class SoftwareWalletCreated(
      val account: SoftwareAccount,
    ) : State

    data class SoftwareWalletFailed(
      val throwable: Throwable?,
    ) : State

    data class SettingUpNotifications(
      val account: SoftwareAccount,
    ) : State
  }
}
