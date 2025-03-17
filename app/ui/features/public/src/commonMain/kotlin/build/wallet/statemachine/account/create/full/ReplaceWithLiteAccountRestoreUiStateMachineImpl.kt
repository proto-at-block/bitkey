package build.wallet.statemachine.account.create.full

import androidx.compose.runtime.*
import bitkey.onboarding.DeleteFullAccountService
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
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

@BitkeyInject(ActivityScope::class)
class ReplaceWithLiteAccountRestoreUiStateMachineImpl(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val deleteFullAccountService: DeleteFullAccountService,
  private val liteAccountBackupToFullAccountUpgrader: LiteAccountBackupToFullAccountUpgrader,
) : ReplaceWithLiteAccountRestoreUiStateMachine {
  @Composable
  override fun model(props: ReplaceWithLiteAccountRestoreUiProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(State.ScanningHardware) }
    val onboardingKeybox = props.keyboxToReplace

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
            onBack = props.onBack,
            screenPresentationStyle = ScreenPresentationStyle.Modal
          )
        )

      is State.DeleteAndRestore -> {
        LaunchedEffect("replace-full-account-and-restore-lite-account") {
          deleteFullAccountService
            .deleteAccount(
              fullAccountId = onboardingKeybox.fullAccountId,
              hardwareProofOfPossession = state.hwFactorProofOfPossession
            )
            .andThen {
              liteAccountBackupToFullAccountUpgrader.upgradeAccount(
                cloudBackup = props.liteAccountCloudBackup,
                onboardingKeybox = onboardingKeybox
              )
            }
            .onSuccess { fullAccount -> props.onAccountUpgraded(fullAccount) }
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
