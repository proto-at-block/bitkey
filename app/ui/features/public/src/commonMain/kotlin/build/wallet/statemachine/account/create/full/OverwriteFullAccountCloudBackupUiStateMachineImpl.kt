package build.wallet.statemachine.account.create.full

import OverwriteExistingBackupConfirmationAlert
import androidx.compose.runtime.*
import bitkey.onboarding.DeleteFullAccountService
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.onboarding.OnboardFullAccountService
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.ui.model.alert.ButtonAlertModel
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class OverwriteFullAccountCloudBackupUiStateMachineImpl(
  private val deleteFullAccountService: DeleteFullAccountService,
  private val hardwareAuthUiStateMachine: HardwareAuthUiStateMachine,
  private val onboardFullAccountService: OnboardFullAccountService,
) : OverwriteFullAccountCloudBackupUiStateMachine {
  @Composable
  override fun model(props: OverwriteFullAccountCloudBackupUiProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(State.ShowingWarningScreen) }

    return when (val state = uiState) {
      State.ShowingWarningScreen -> {
        var alert by remember { mutableStateOf<ButtonAlertModel?>(null) }

        OverwriteFullAccountCloudBackupWarningModel(
          onOverwriteExistingBackup = {
            alert = OverwriteExistingBackupConfirmationAlert(
              onConfirm = props.onOverwrite,
              onCancel = {
                alert = null
              }
            )
          },
          onCancel = { uiState = State.ScanningHardwareForCancellation }
        ).asRootScreen(alertModel = alert)
      }
      State.ScanningHardwareForCancellation -> {
        val keybox = props.keybox
        val account = FullAccount(
          accountId = keybox.fullAccountId,
          keybox = keybox
        )
        hardwareAuthUiStateMachine.model(
          HardwareAuthUiProps(
            account = account,
            actionProofType = ActionProofType.DeleteAccount(
              accountId = keybox.fullAccountId.serverId
            ),
            segment = OnboardingAppSegment.FullAccount,
            actionDescription = "Canceling account creation",
            screenPresentationStyle = ScreenPresentationStyle.Root,
            onSuccess = { proof ->
              uiState = State.DeletingAccountForCancellation(proof)
            },
            onBack = { uiState = State.ShowingWarningScreen },
            shouldLock = false
          )
        )
      }
      is State.DeletingAccountForCancellation -> {
        LaunchedEffect("deleting-account") {
          deleteFullAccountService
            .deleteAccount(
              props.keybox.fullAccountId,
              state.proof
            ).andThen {
              onboardFullAccountService.cancelAccountCreation()
            }
            .onFailure {
              uiState = State.Failed
            }
            .onSuccess {
              props.rollback()
            }
        }
        LoadingBodyModel(
          id = CloudEventTrackerScreenId.DELETING_FULL_ACCOUNT
        ).asRootScreen()
      }
      State.Failed ->
        OverwriteFullAccountCloudBackupFailureModel(
          onBack = { uiState = State.ShowingWarningScreen },
          onRetry = { uiState = State.ScanningHardwareForCancellation }
        ).asRootScreen()
    }
  }

  private sealed interface State {
    /**
     * Showing the warning screen, presenting the option to overwrite the cloud backup or cancel.
     */
    data object ShowingWarningScreen : State

    /**
     * During cancellation, scanning hardware for proof of possession to delete the account on f8e.
     */
    data object ScanningHardwareForCancellation : State

    /** During cancellation, deleting the onboarding account. */
    data class DeletingAccountForCancellation(
      val proof: PrivilegedActionProof,
    ) : State

    /**
     * A failure occurred either during [ScanningHardwareForCancellation] or
     * [DeletingAccountForCancellation].
     */
    data object Failed : State
  }
}
