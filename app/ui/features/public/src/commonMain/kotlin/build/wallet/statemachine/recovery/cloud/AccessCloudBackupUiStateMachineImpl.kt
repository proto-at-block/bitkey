package build.wallet.statemachine.recovery.cloud

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.CloudEventTrackerScreenIdContext.APP_RECOVERY
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupError.RectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.isFullAccount
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.cloud.CloudSignInFailedScreenModel
import build.wallet.statemachine.cloud.RectifiableErrorHandlingProps
import build.wallet.statemachine.cloud.RectifiableErrorHandlingUiStateMachine
import build.wallet.statemachine.cloud.RectifiableErrorMessages.Companion.RectifiableErrorAccessMessages
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.data.keybox.AccountData.StartIntent
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachineImpl.State.*
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class AccessCloudBackupUiStateMachineImpl(
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudSignInUiStateMachine: CloudSignInUiStateMachine,
  private val rectifiableErrorHandlingUiStateMachine: RectifiableErrorHandlingUiStateMachine,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : AccessCloudBackupUiStateMachine {
  @Composable
  override fun model(props: AccessCloudBackupUiProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(
        SigningIntoCloudUiState
      )
    }

    return when (val currentState = state) {
      is SigningIntoCloudUiState ->
        cloudSignInUiStateMachine.model(
          props =
            CloudSignInUiProps(
              forceSignOut = true,
              onSignedIn = { account ->
                state = CheckingCloudBackupUiState(account)
              },
              onSignInFailure = {
                state = CloudNotSignedInUiState
              },
              eventTrackerContext = APP_RECOVERY
            )
        ).asRootScreen()
      is ShowingCustomerSupportUiState ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = currentState.urlString,
              onClose = {
                state = CloudNotSignedInUiState
              }
            )
          }
        ).asModalScreen()
      is CloudNotSignedInUiState ->
        if (props.showErrorOnBackupMissing) {
          CloudNotSignedInBodyModel(
            onBack = props.onExit,
            onCannotAccessCloud = {
              onCannotAccessCloudBackup(
                intent = props.startIntent,
                inviteCode = props.inviteCode,
                onStartLostAppRecovery = props.onStartLostAppRecovery,
                onStartLiteAccountCreation = props.onStartLiteAccountCreation
              )
            },
            onCheckCloudAgain = {
              state = SigningIntoCloudUiState
            },
            onImportEmergencyExitKit = props.onImportEmergencyExitKit,
            onShowTroubleshootingSteps = {
              state = ShowingTroubleshootingSteps(fromState = currentState)
            }
          ).asRootScreen()
        } else {
          CloudSignInFailedScreenModel(
            onContactSupport = {
              state = ShowingCustomerSupportUiState(
                urlString = "https://support.bitkey.world/hc/en-us"
              )
            },
            onBack = props.onExit,
            onTryAgain = {
              state = SigningIntoCloudUiState
            },
            devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
          ).asRootScreen()
        }

      is CheckingCloudBackupUiState -> {
        LaunchedEffect("check cloud account for backup") {
          cloudBackupRepository.readActiveBackup(currentState.account)
            .onSuccess { backup ->
              when (backup) {
                null -> if (props.showErrorOnBackupMissing) {
                  state = CloudBackupNotFoundUiState(currentState.account)
                } else {
                  onCannotAccessCloudBackup(
                    intent = props.startIntent,
                    inviteCode = props.inviteCode,
                    onStartLostAppRecovery = props.onStartLostAppRecovery,
                    onStartLiteAccountCreation = props.onStartLiteAccountCreation
                  )
                }
                else -> handleExistingBackupFound(
                  backup = backup,
                  inviteCode = props.inviteCode,
                  onStartCloudRecovery = props.onStartCloudRecovery,
                  onStartLiteAccountRecovery = props.onStartLiteAccountRecovery
                )
              }
            }
            .onFailure { cloudBackupError ->
              when (cloudBackupError) {
                is RectifiableCloudBackupError -> {
                  state = CloudBackupRectifiableErrorState(
                    cloudStoreAccount = currentState.account,
                    rectifiableCloudBackupError = cloudBackupError
                  )
                }

                is UnrectifiableCloudBackupError -> if (props.showErrorOnBackupMissing) {
                  state = CloudBackupNotFoundUiState(currentState.account)
                } else {
                  onCannotAccessCloudBackup(
                    intent = props.startIntent,
                    inviteCode = props.inviteCode,
                    onStartLostAppRecovery = props.onStartLostAppRecovery,
                    onStartLiteAccountCreation = props.onStartLiteAccountCreation
                  )
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
            onCannotAccessCloudBackup(
              intent = props.startIntent,
              inviteCode = props.inviteCode,
              onStartLostAppRecovery = props.onStartLostAppRecovery,
              onStartLiteAccountCreation = props.onStartLiteAccountCreation
            )
          },
          onCheckCloudAgain = {
            state = SigningIntoCloudUiState
          },
          onImportEmergencyExitKit = props.onImportEmergencyExitKit,
          onShowTroubleshootingSteps = {
            state = ShowingTroubleshootingSteps(fromState = currentState)
          }
        ).asRootScreen()

      is ShowingTroubleshootingSteps ->
        CloudBackupTroubleshootingStepsModel(
          onBack = { state = currentState.fromState },
          onTryAgain = { state = SigningIntoCloudUiState }
        ).asModalScreen()

      is CloudBackupRectifiableErrorState ->
        rectifiableErrorHandlingUiStateMachine.model(
          props =
            RectifiableErrorHandlingProps(
              messages = RectifiableErrorAccessMessages,
              rectifiableError = currentState.rectifiableCloudBackupError,
              cloudStoreAccount = currentState.cloudStoreAccount,
              onFailure = {
                props.onExit()
              },
              onReturn = {
                state = CheckingCloudBackupUiState(currentState.cloudStoreAccount)
              },
              screenId = CloudEventTrackerScreenId.LOADING_RESTORING_FROM_CLOUD_BACKUP,
              presentationStyle = Root,
              errorData = null
            )
        )
    }
  }

  private fun handleExistingBackupFound(
    backup: CloudBackup,
    inviteCode: String?,
    onStartCloudRecovery: (CloudBackup) -> Unit,
    onStartLiteAccountRecovery: (CloudBackup) -> Unit,
  ) {
    if (backup.isFullAccount()) {
      onStartCloudRecovery(backup)
    } else if (inviteCode != null) {
      Router.route = Route.TrustedContactInvite(inviteCode)
    } else {
      onStartLiteAccountRecovery(backup)
    }
  }

  private fun onCannotAccessCloudBackup(
    intent: StartIntent,
    inviteCode: String?,
    onStartLostAppRecovery: () -> Unit,
    onStartLiteAccountCreation: (String?, StartIntent) -> Unit,
  ) {
    when (intent) {
      StartIntent.RestoreBitkey -> {
        // If the customer can't sign in or no backup is available, fall back to Lost App recovery.
        onStartLostAppRecovery()
      }
      StartIntent.BeTrustedContact, StartIntent.BeBeneficiary -> {
        // For TC/Beneficiary flows with no accessible backup, start lite account creation.
        onStartLiteAccountCreation(inviteCode, intent)
      }
    }
  }

  private sealed interface State {
    /**
     * Checking to see if we have a Cloud account logged in already / initiating the Google Sign
     * In external activity on Android. Shows a loading spinner.
     */
    data object SigningIntoCloudUiState : State

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

    data class ShowingCustomerSupportUiState(val urlString: String) : State
  }
}
