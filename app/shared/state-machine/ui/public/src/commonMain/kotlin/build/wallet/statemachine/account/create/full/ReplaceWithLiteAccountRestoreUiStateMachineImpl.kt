package build.wallet.statemachine.account.create.full

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.auth.OnboardingFullAccountDeleter
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.onboarding.LiteAccountBackupToFullAccountUpgrader
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.cloud.SAVING_BACKUP_MESSAGE
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class ReplaceWithLiteAccountRestoreUiStateMachineImpl(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val onboardingFullAccountDeleter: OnboardingFullAccountDeleter,
  private val liteAccountBackupToFullAccountUpgrader: LiteAccountBackupToFullAccountUpgrader,
) : ReplaceWithLiteAccountRestoreUiStateMachine {
  @Composable
  override fun model(props: ReplaceWithLiteAccountRestoreUiProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(State.ScanningHardware) }
    val onboardingKeybox = props.data.keyboxToReplace

    return when (val state = uiState) {
      is State.ScanningHardware ->
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            request =
              Request.HwKeyProof(
                onSuccess = { proof ->
                  uiState = State.DeleteAndRestore(proof)
                }
              ),
            fullAccountId = onboardingKeybox.fullAccountId,
            fullAccountConfig = onboardingKeybox.config,
            onBack = props.data.onBack,
            screenPresentationStyle = ScreenPresentationStyle.Modal
          )
        )

      is State.DeleteAndRestore -> {
        LaunchedEffect("replace-full-account-and-restore-lite-account") {
          onboardingFullAccountDeleter
            .deleteAccount(
              fullAccountId = onboardingKeybox.fullAccountId,
              f8eEnvironment = onboardingKeybox.config.f8eEnvironment,
              hardwareProofOfPossession = state.hwFactorProofOfPossession
            )
            .andThen {
              liteAccountBackupToFullAccountUpgrader.upgradeAccount(
                cloudBackup = props.data.liteAccountCloudBackup,
                onboardingKeybox = onboardingKeybox
              )
            }
            .onSuccess { fullAccount -> props.data.onAccountUpgraded(fullAccount) }
            .onFailure { uiState = State.Failed(it) }
        }
        LoadingBodyModel(
          message = SAVING_BACKUP_MESSAGE,
          onBack = {},
          id = CloudEventTrackerScreenId.LOADING_RESTORING_FROM_LITE_ACCOUNT_CLOUD_BACKUP_DURING_FULL_ACCOUNT_ONBOARDING
        ).asRootScreen()
      }

      is State.Failed ->
        // The process of deleting the full account and restoring the lite account is unknown to
        // the user, so any error we encounter here surface as failure to save the full account
        // backup.
        CloudBackupFailedScreenModel(
          eventTrackerScreenId = CloudEventTrackerScreenId.FAILURE_RESTORE_FROM_LITE_ACCOUNT_CLOUD_BACKUP_AFTER_ONBOARDING,
          error = state.error,
          onTryAgain = { uiState = State.ScanningHardware }
        ).asRootScreen()
    }
  }

  private sealed interface State {
    data object ScanningHardware : State

    data class DeleteAndRestore(val hwFactorProofOfPossession: HwFactorProofOfPossession) : State

    data class Failed(val error: Error) : State
  }
}
