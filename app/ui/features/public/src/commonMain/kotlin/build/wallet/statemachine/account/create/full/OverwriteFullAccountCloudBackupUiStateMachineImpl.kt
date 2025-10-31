package build.wallet.statemachine.account.create.full

import OverwriteExistingBackupConfirmationAlert
import androidx.compose.runtime.*
import bitkey.onboarding.DeleteFullAccountService
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.onboarding.OnboardingKeyboxStepStateDao
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
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
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val onboardingKeyboxStepStateDao: OnboardingKeyboxStepStateDao,
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
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            fullAccountId = props.keybox.fullAccountId,
            request =
              Request.HwKeyProof(
                onSuccess = { proof ->
                  uiState = State.DeletingAccountForCancellation(proof)
                }
              ),
            onBack = { uiState = State.ShowingWarningScreen },
            screenPresentationStyle = ScreenPresentationStyle.Root
          )
        )
      }
      is State.DeletingAccountForCancellation -> {
        LaunchedEffect("deleting-account") {
          deleteFullAccountService
            .deleteAccount(
              props.keybox.fullAccountId,
              state.proofOfPossession
            ).andThen {
              // TODO (W-14909): This should probably be OnboardFullAccountService#cancelAccountCreation()
              // but it wasn't here before, so this is a targeted fix to make sure that we clear the
              // descriptor backup step if you abandon onboarding at this point
              onboardingKeyboxStepStateDao.clear()
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
      val proofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * A failure occurred either during [ScanningHardwareForCancellation] or
     * [DeletingAccountForCancellation].
     */
    data object Failed : State
  }
}
