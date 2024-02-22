package build.wallet.statemachine.recovery.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.CloudEventTrackerScreenIdContext.APP_RECOVERY
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.backup.CloudBackupError.RectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.emergencyaccesskit.EmergencyAccessKitAssociation
import build.wallet.recovery.emergencyaccess.EmergencyAccessFeatureFlag
import build.wallet.statemachine.cloud.RectifiableErrorHandlingProps
import build.wallet.statemachine.cloud.RectifiableErrorHandlingUiStateMachine
import build.wallet.statemachine.cloud.RectifiableErrorMessages.Companion.RectifiableErrorAccessMessages
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachineImpl.State.CheckingCloudBackupUiState
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachineImpl.State.CloudBackupNotFoundUiState
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachineImpl.State.CloudBackupRectifiableErrorState
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachineImpl.State.CloudNotSignedInUiState
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachineImpl.State.ShowingTroubleshootingSteps
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachineImpl.State.SigningIntoCloudUiState
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class AccessCloudBackupUiStateMachineImpl(
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudSignInUiStateMachine: CloudSignInUiStateMachine,
  private val rectifiableErrorHandlingUiStateMachine: RectifiableErrorHandlingUiStateMachine,
  private val emergencyAccessFeatureFlag: EmergencyAccessFeatureFlag,
) : AccessCloudBackupUiStateMachine {
  @Composable
  override fun model(props: AccessCloudBackupUiProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(
        SigningIntoCloudUiState(forceSignOut = props.forceSignOutFromCloud)
      )
    }

    val eakEnabled = remember { emergencyAccessFeatureFlag.flagValue() }.collectAsState()
    val eakBuild = remember {
      props.eakAssociation == EmergencyAccessKitAssociation.EakBuild
    }

    val onImportEmergencyAccessKit: (() -> Unit)? =
      if (eakEnabled.value.value || eakBuild) {
        props.onImportEmergencyAccessKit
      } else {
        null
      }

    return when (val currentState = state) {
      is SigningIntoCloudUiState ->
        cloudSignInUiStateMachine.model(
          props =
            CloudSignInUiProps(
              forceSignOut = currentState.forceSignOut,
              onSignedIn = { account ->
                state = CheckingCloudBackupUiState(account)
              },
              onSignInFailure = {
                state = CloudNotSignedInUiState
              },
              eventTrackerContext = APP_RECOVERY
            )
        ).asRootScreen()

      is CloudNotSignedInUiState ->
        CloudNotSignedInBodyModel(
          onBack = props.onExit,
          onCannotAccessCloud = {
            props.onCannotAccessCloudBackup(null)
          },
          onCheckCloudAgain = {
            state = SigningIntoCloudUiState(forceSignOut = true)
          },
          onImportEmergencyAccessKit = onImportEmergencyAccessKit,
          onShowTroubleshootingSteps = {
            state = ShowingTroubleshootingSteps(fromState = currentState)
          }
        ).asRootScreen()

      is CheckingCloudBackupUiState -> {
        LaunchedEffect("check cloud account for backup") {
          cloudBackupRepository.readBackup(currentState.account)
            .onSuccess { backup ->
              when (backup) {
                null ->
                  if (props.showErrorOnBackupMissing) {
                    state = CloudBackupNotFoundUiState(currentState.account)
                  } else {
                    props.onCannotAccessCloudBackup(currentState.account)
                  }
                else -> props.onBackupFound(backup)
              }
            }
            .onFailure { cloudBackupError ->
              when (cloudBackupError) {
                is RectifiableCloudBackupError -> {
                  state =
                    CloudBackupRectifiableErrorState(
                      cloudStoreAccount = currentState.account,
                      rectifiableCloudBackupError = cloudBackupError
                    )
                }

                is UnrectifiableCloudBackupError ->
                  if (props.showErrorOnBackupMissing) {
                    state = CloudBackupNotFoundUiState(currentState.account)
                  } else {
                    props.onCannotAccessCloudBackup(currentState.account)
                  }
              }
            }
        }

        LoadingBodyModel(
          message = "Looking for your backup...",
          onBack = props.onExit,
          id = CloudEventTrackerScreenId.CHECKING_CLOUD_BACKUP_AVAILABILITY
        ).asRootScreen()
      }

      is CloudBackupNotFoundUiState ->
        CloudBackupNotFoundBodyModel(
          onBack = props.onExit,
          onCannotAccessCloud = {
            props.onCannotAccessCloudBackup(currentState.account)
          },
          onCheckCloudAgain = {
            state = SigningIntoCloudUiState(forceSignOut = true)
          },
          onImportEmergencyAccessKit = onImportEmergencyAccessKit,
          onShowTroubleshootingSteps = {
            state = ShowingTroubleshootingSteps(fromState = currentState)
          }
        ).asRootScreen()

      is ShowingTroubleshootingSteps ->
        CloudBackupTroubleshootingStepsModel(
          onBack = { state = currentState.fromState },
          onTryAgain = { state = SigningIntoCloudUiState(forceSignOut = true) }
        ).asModalScreen()

      is CloudBackupRectifiableErrorState ->
        rectifiableErrorHandlingUiStateMachine.model(
          props =
            RectifiableErrorHandlingProps(
              messages = RectifiableErrorAccessMessages,
              rectifiableError = currentState.rectifiableCloudBackupError,
              cloudStoreAccount = currentState.cloudStoreAccount,
              onFailure = props.onExit,
              onReturn = {
                state = CheckingCloudBackupUiState(currentState.cloudStoreAccount)
              },
              screenId = CloudEventTrackerScreenId.LOADING_RESTORING_FROM_CLOUD_BACKUP,
              presentationStyle = Root
            )
        )
    }
  }

  private sealed interface State {
    /**
     * Checking to see if we have a Cloud account logged in already / initiating the Google Sign
     * In external activity on Android. Shows a loading spinner.
     *
     * @property forceSignOut - indicates if we are logging out from any existing account first.
     */
    data class SigningIntoCloudUiState(val forceSignOut: Boolean) : State

    /**
     * The Cloud account sign in failed / was not logged in.
     */
    data object CloudNotSignedInUiState : State

    /**
     * Using logged in cloud account, access cloud storage and look for a backup.
     */
    data class CheckingCloudBackupUiState(val account: CloudStoreAccount) : State

    /**
     * Could not find wallet backup on the cloud storage.
     */
    data class CloudBackupNotFoundUiState(val account: CloudStoreAccount?) : State

    /**
     * Could not find cloud backup and showing iOS troubleshooting modal.
     */
    data class ShowingTroubleshootingSteps(val fromState: State) : State

    /**
     * Could not access wallet backup, but there may be a resolution.
     */
    data class CloudBackupRectifiableErrorState(
      val cloudStoreAccount: CloudStoreAccount,
      val rectifiableCloudBackupError: RectifiableCloudBackupError,
    ) : State
  }
}
