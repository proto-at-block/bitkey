package build.wallet.statemachine.cloud.health

import FoundCloudBackupForDifferentAccountModel
import OverwriteExistingBackupConfirmationAlert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.CloudEventTrackerScreenIdContext.BACKUP_REPAIR
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CREATING_CLOUD_BACKUP
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.PREPARING_CLOUD_BACKUP
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_NOT_SIGNED_IN
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.UPLOADING_CLOUD_BACKUP
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupError
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.FullAccountCloudBackupCreator
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.csek.CsekGenerator
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.isFullAccount
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.emergencyaccesskit.EmergencyAccessKitData
import build.wallet.emergencyaccesskit.EmergencyAccessKitPdfGenerator
import build.wallet.emergencyaccesskit.EmergencyAccessKitRepository
import build.wallet.emergencyaccesskit.EmergencyAccessKitRepositoryError
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.recovery.socrec.SocRecRelationshipsRepository
import build.wallet.statemachine.cloud.CloudSignInFailedScreenModel
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.CheckingLocalBackupState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.CheckingMobileKeyCloudBackupState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.CreatingEakBackupState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.CreatingMobileKeyBackupState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.ErrorCheckingCloudBackupState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.ErrorCreatingBackupState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.ErrorSigningIntoCloudState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.ErrorUploadingBackupState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.FoundBackupForDifferentAccountState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.GeneratingCsekState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.ProblemWithBackupState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.SealingCsekWithHardwareState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.ShowingCustomerSupportUiState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.SigningIntoCloudState
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.SyncingBackupHealthStatus
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachineImpl.State.UploadingBackupState
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.cloud.CloudSignInUiProps
import build.wallet.statemachine.recovery.cloud.CloudSignInUiStateMachine
import build.wallet.ui.model.alert.AlertModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.first

// TODO(796): add integration tests
class RepairCloudBackupStateMachineImpl(
  private val cloudSignInStateMachine: CloudSignInUiStateMachine,
  private val cloudBackupDao: CloudBackupDao,
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val csekGenerator: CsekGenerator,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val csekDao: CsekDao,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
  private val socRecRelationshipsRepository: SocRecRelationshipsRepository,
  private val emergencyAccessKitPdfGenerator: EmergencyAccessKitPdfGenerator,
  private val emergencyAccessKitRepository: EmergencyAccessKitRepository,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : RepairCloudBackupStateMachine {
  @Composable
  override fun model(props: RepairMobileKeyBackupProps): ScreenModel {
    var state: State by remember {
      // Do not force sign out, as we want to check if we are already signed in initially.
      mutableStateOf(SigningIntoCloudState(forceSignOut = false))
    }

    return when (val currentState = state) {
      is SigningIntoCloudState ->
        cloudSignInStateMachine.model(
          CloudSignInUiProps(
            forceSignOut = currentState.forceSignOut,
            onSignedIn = { cloudAccount ->
              log { "Signed into cloud account." }
              state = CheckingLocalBackupState(cloudAccount)
            },
            onSignInFailure = {
              log { "Error signing into cloud account." }
              state = ErrorSigningIntoCloudState
            },
            eventTrackerContext = BACKUP_REPAIR
          )
        ).asScreen(props.presentationStyle)

      is ShowingCustomerSupportUiState ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = currentState.urlString,
              onClose = {
                state = ErrorSigningIntoCloudState
              }
            )
          }
        ).asModalScreen()
      is ErrorSigningIntoCloudState ->
        CloudSignInFailedScreenModel(
          onContactSupport = {
            state = ShowingCustomerSupportUiState(
              urlString = "https://support.bitkey.world/hc/en-us"
            )
          },
          onTryAgain = {
            // Try signing in again, this time force sign out to allow customer to resign in or
            // switch accounts.
            log { "Retrying signing into cloud account." }
            state = SigningIntoCloudState(forceSignOut = true)
          },
          onBack = props.onExit,
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
        ).asScreen(props.presentationStyle)

      is CheckingLocalBackupState -> {
        LaunchedEffect("check-local-backup") {
          log { "Checking local storage for cloud backup." }
          cloudBackupDao
            .get(props.account.accountId.serverId)
            .onSuccess { localBackup ->
              state = currentState.determineNextState(localBackup)
            }
            .onFailure {
              log { "Error checking local backup, falling back on creating new backup." }
              state = GeneratingCsekState(currentState.cloudAccount)
            }
        }

        preparingBackupModel(props)
      }

      is CheckingMobileKeyCloudBackupState -> {
        LaunchedEffect("check-cloud-backup") {
          log { "Checking cloud backup" }

          cloudBackupRepository
            .readBackup(currentState.cloudAccount)
            .onSuccess { cloudBackup ->
              state = currentState.determineNextState(props, cloudBackup)
            }
            .onFailure {
              log { "Error checking existing cloud backup." }
              state = ErrorCheckingCloudBackupState(
                cloudAccount = currentState.cloudAccount,
                mobileKeyBackup = currentState.mobileKeyBackup,
                eakBackup = currentState.eakBackup
              )
            }
        }

        preparingBackupModel(props)
      }

      is ErrorCheckingCloudBackupState ->
        errorConnectingToCloud(
          props,
          onTryAgain = {
            state = CheckingMobileKeyCloudBackupState(
              cloudAccount = currentState.cloudAccount,
              mobileKeyBackup = currentState.mobileKeyBackup,
              eakBackup = currentState.eakBackup
            )
          }
        )

      is GeneratingCsekState -> {
        LaunchedEffect("generating-csek") {
          log { "Generating CSEK" }

          val csek = csekGenerator.generate()
          state = SealingCsekWithHardwareState(
            cloudAccount = currentState.cloudAccount,
            csek = csek
          )
        }

        preparingBackupModel(props)
      }

      is SealingCsekWithHardwareState -> {
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              commands.sealKey(session, currentState.csek)
            },
            onSuccess = { sealedCsek ->
              csekDao.set(sealedCsek, currentState.csek)
                .onSuccess {
                  state = CreatingMobileKeyBackupState(
                    cloudAccount = currentState.cloudAccount,
                    sealedCsek = sealedCsek
                  )
                }
                .onFailure {
                  state = ErrorCreatingBackupState(
                    cloudAccount = currentState.cloudAccount
                  )
                }
            },
            onCancel = props.onExit,
            isHardwareFake = props.account.keybox.config.isHardwareFake,
            needsAuthentication = false,
            screenPresentationStyle = props.presentationStyle,
            eventTrackerContext = NfcEventTrackerScreenIdContext.METADATA
          )
        )
      }

      is ErrorCreatingBackupState -> {
        ErrorCreatingBackupModel(
          onClose = {
            props.onExit()
          },
          onRetry = {
            state = GeneratingCsekState(currentState.cloudAccount)
          }
        ).asScreen(props.presentationStyle)
      }

      is FoundBackupForDifferentAccountState -> {
        var alert by remember { mutableStateOf<AlertModel?>(null) }

        FoundCloudBackupForDifferentAccountModel(
          onOverwriteExistingBackup = {
            alert = OverwriteExistingBackupConfirmationAlert(
              onConfirm = {
                state = UploadingBackupState(
                  cloudAccount = currentState.cloudAccount,
                  mobileKeyBackup = currentState.mobileKeyBackup,
                  eakBackup = currentState.eakBackup
                )
              },
              onCancel = {
                alert = null
              }
            )
          },
          onClose = props.onExit,
          onTryAgain = {
            // Try signing in again, this time force sign out to allow customer to resign in or
            // switch accounts.
            log { "Retrying signing into cloud account." }
            state = SigningIntoCloudState(forceSignOut = true)
          },
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
        ).asScreen(props.presentationStyle, alertModel = alert)
      }

      ProblemWithBackupState -> {
        ProblemWithCloudBackupFormModel(
          onClose = props.onExit,
          onContinue = {
          }
        ).asScreen(props.presentationStyle)
      }

      is CreatingMobileKeyBackupState -> {
        LaunchedEffect("creating-mobile-key-backup") {
          log { "Creating mobile key backup" }
          val trustedContacts = socRecRelationshipsRepository.relationships
            .first()
            .trustedContacts

          fullAccountCloudBackupCreator
            .create(
              keybox = props.account.keybox,
              sealedCsek = currentState.sealedCsek,
              trustedContacts = trustedContacts
            )
            .onSuccess { mobileKeyBackup ->
              state = CreatingEakBackupState(
                cloudAccount = currentState.cloudAccount,
                sealedCsek = currentState.sealedCsek,
                mobileKeyBackup = mobileKeyBackup
              )
            }
            .onFailure {
              state = ErrorCreatingBackupState(
                cloudAccount = currentState.cloudAccount
              )
            }
        }

        creatingBackupModel(props)
      }

      is CreatingEakBackupState -> {
        LaunchedEffect("creating-eak-backup") {
          log { "Creating EAK backup" }

          // Create the emergency access kit.
          emergencyAccessKitPdfGenerator
            .generate(
              keybox = props.account.keybox,
              sealedCsek = currentState.sealedCsek
            )
            .onFailure {
              state = ErrorCreatingBackupState(
                cloudAccount = currentState.cloudAccount
              )
            }
            .onSuccess { eakBackup ->
              state = CheckingMobileKeyCloudBackupState(
                cloudAccount = currentState.cloudAccount,
                mobileKeyBackup = currentState.mobileKeyBackup,
                eakBackup = eakBackup
              )
            }
        }

        creatingBackupModel(props)
      }

      is UploadingBackupState -> {
        LaunchedEffect("upload-backup") {
          log { "Uploading backups to cloud" }

          // Uploading Mobile Key backup to cloud
          cloudBackupRepository
            .writeBackup(
              accountId = props.account.keybox.fullAccountId,
              cloudStoreAccount = currentState.cloudAccount,
              backup = currentState.mobileKeyBackup,
              requireAuthRefresh = true
            )
            .logFailure { "Error saving cloud backup to cloud storage" }
            .onFailure { error ->
              state = when (error) {
                is CloudBackupError.RectifiableCloudBackupError ->
                  SigningIntoCloudState(forceSignOut = false)
                else -> ErrorUploadingBackupState(
                  currentState.cloudAccount,
                  currentState.mobileKeyBackup,
                  currentState.eakBackup
                )
              }
            }
            .onSuccess {
              // proceed to uploading EAK
            }

          // Uploading EAK backup to cloud
          emergencyAccessKitRepository
            .write(
              account = currentState.cloudAccount,
              emergencyAccessKitData = currentState.eakBackup
            )
            .logFailure { "Error saving emergency access kit to cloud file store" }
            .onFailure { error ->
              state = when (error) {
                is EmergencyAccessKitRepositoryError.RectifiableCloudError ->
                  SigningIntoCloudState(forceSignOut = false)
                else -> ErrorUploadingBackupState(
                  currentState.cloudAccount,
                  currentState.mobileKeyBackup,
                  currentState.eakBackup
                )
              }
            }
            .onSuccess {
              state = SyncingBackupHealthStatus
            }
        }

        uploadingBackupToCloudModel(props)
      }

      is SyncingBackupHealthStatus -> {
        LaunchedEffect("sync-backup-health-status") {
          val status = cloudBackupHealthRepository.performSync(props.account)
          props.onRepaired(status)
        }

        uploadingBackupToCloudModel(props)
      }

      is ErrorUploadingBackupState -> errorUploadingBackupModel(
        props,
        onTryAgain = {
          state = UploadingBackupState(
            currentState.cloudAccount,
            currentState.mobileKeyBackup,
            currentState.eakBackup
          )
        }
      )
    }
  }

  private fun errorUploadingBackupModel(
    props: RepairMobileKeyBackupProps,
    onTryAgain: () -> Unit,
  ): ScreenModel {
    return ErrorFormBodyModel(
      onBack = props.onExit,
      title = "We were unable to upload backup to ${cloudServiceProvider().name}",
      subline = "Please try again.",
      primaryButton = ButtonDataModel(text = "Retry", onClick = onTryAgain),
      eventTrackerScreenId = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT
    ).asScreen(props.presentationStyle)
  }

  private fun preparingBackupModel(props: RepairMobileKeyBackupProps): ScreenModel {
    return LoadingBodyModel(
      message = "Preparing backup",
      onBack = props.onExit,
      id = PREPARING_CLOUD_BACKUP
    ).asScreen(props.presentationStyle)
  }

  private fun creatingBackupModel(props: RepairMobileKeyBackupProps): ScreenModel {
    return LoadingBodyModel(
      message = "Creating backup",
      onBack = props.onExit,
      id = CREATING_CLOUD_BACKUP
    ).asScreen(props.presentationStyle)
  }

  private fun uploadingBackupToCloudModel(props: RepairMobileKeyBackupProps): ScreenModel {
    return LoadingBodyModel(
      message = "Uploading backup to ${cloudServiceProvider().name}",
      onBack = props.onExit,
      id = UPLOADING_CLOUD_BACKUP
    ).asScreen(props.presentationStyle)
  }

  private fun errorConnectingToCloud(
    props: RepairMobileKeyBackupProps,
    onTryAgain: () -> Unit,
  ): ScreenModel {
    return ErrorFormBodyModel(
      onBack = props.onExit,
      title = "We were unable to connect to ${cloudServiceProvider().name}",
      subline = "Please try again.",
      primaryButton = ButtonDataModel(text = "Retry", onClick = onTryAgain),
      eventTrackerScreenId = SAVE_CLOUD_BACKUP_NOT_SIGNED_IN
    ).asScreen(props.presentationStyle)
  }

  private sealed interface State {
    /**
     * Signing in to cloud, or checking if already signed in.
     */
    data class SigningIntoCloudState(val forceSignOut: Boolean) : State

    data object ErrorSigningIntoCloudState : State

    data class ErrorUploadingBackupState(
      val cloudAccount: CloudStoreAccount,
      val mobileKeyBackup: CloudBackup,
      val eakBackup: EmergencyAccessKitData,
    ) : State

    /**
     * Cloud backup found, but it belongs to a different account. Customer has an option to
     * overwrite the backup, or cancel the repair process.
     */
    data class FoundBackupForDifferentAccountState(
      val cloudAccount: CloudStoreAccount,
      val mobileKeyBackup: CloudBackup,
      val eakBackup: EmergencyAccessKitData,
    ) : State

    /**
     * A problem detected with the cloud backup (it's missing or invalid). We will need to recreate
     * (if needed) backup and upload it.
     */
    data object ProblemWithBackupState : State

    /**
     * Checking local storage to see if we already have a backup that we can
     * re-upload.
     */
    data class CheckingLocalBackupState(
      val cloudAccount: CloudStoreAccount,
    ) : State {
      /**
       * Determine the next state based on the local backup found.
       */
      fun determineNextState(foundLocalBackup: CloudBackup?): State {
        when (foundLocalBackup) {
          null -> {
            log { "No local cloud backup found, creating new backup." }
            return GeneratingCsekState(cloudAccount)
          }
          is CloudBackupV2 -> {
            if (foundLocalBackup.isFullAccount()) {
              return CreatingEakBackupState(
                cloudAccount = cloudAccount,
                sealedCsek = foundLocalBackup.fullAccountFields!!.sealedHwEncryptionKey,
                mobileKeyBackup = foundLocalBackup
              )
            } else {
              // Should not happen, let's create a new backup
              log(Error) {
                "Found a non-full local account backup for a full account"
              }
              return GeneratingCsekState(cloudAccount)
            }
          }
        }
      }
    }

    /**
     * Checking the state of the existing cloud backup. If any, make sure it belongs to
     * the current customer's account. If not, the customer will have an option to overwrite it.
     */
    data class CheckingMobileKeyCloudBackupState(
      val cloudAccount: CloudStoreAccount,
      val mobileKeyBackup: CloudBackup,
      val eakBackup: EmergencyAccessKitData,
    ) : State {
      /**
       * Determine the next state based on the existing cloud backup found.
       *
       * @param foundMobileKeyBackup the Mobile Key backup that we found on cloud.
       */
      fun determineNextState(
        props: RepairMobileKeyBackupProps,
        foundMobileKeyBackup: CloudBackup?,
      ): State {
        if (foundMobileKeyBackup == null) {
          log { "No existing cloud backup found, uploading new backup." }
          return UploadingBackupState(cloudAccount, mobileKeyBackup, eakBackup)
        } else {
          when (foundMobileKeyBackup) {
            is CloudBackupV2 -> {
              val sameAccount = foundMobileKeyBackup.accountId == props.account.accountId.serverId
              return if (sameAccount) {
                // Found a backup for the current account, okay to go ahead and overwrite it.
                log { "Found a backup for the current account." }
                UploadingBackupState(cloudAccount, mobileKeyBackup, eakBackup)
              } else {
                // Found a backup for a different account, customer will have an option to overwrite it.
                log { "Found a backup for a different account." }
                FoundBackupForDifferentAccountState(
                  cloudAccount = cloudAccount,
                  mobileKeyBackup = mobileKeyBackup,
                  eakBackup = eakBackup
                )
              }
            }
          }
        }
      }
    }

    data class ErrorCheckingCloudBackupState(
      val cloudAccount: CloudStoreAccount,
      val mobileKeyBackup: CloudBackup,
      val eakBackup: EmergencyAccessKitData,
    ) : State

    /**
     * App generates a new CSEK to be used for sealing the backup. The CSEK will then
     * be sealed with the hardware and used to encrypt the backup.
     */
    data class GeneratingCsekState(
      val cloudAccount: CloudStoreAccount,
    ) : State

    /**
     * Sealing a new CSEK with the hardware. The sealed CSEK will be used to encrypt the backup.
     */
    data class SealingCsekWithHardwareState(
      val cloudAccount: CloudStoreAccount,
      val csek: Csek,
    ) : State

    data class ErrorCreatingBackupState(
      val cloudAccount: CloudStoreAccount,
    ) : State

    /**
     * Creating a new mobile key backup.
     */
    data class CreatingMobileKeyBackupState(
      val cloudAccount: CloudStoreAccount,
      val sealedCsek: SealedCsek,
    ) : State

    /**
     * Creating a new EAK backup.
     */
    data class CreatingEakBackupState(
      val cloudAccount: CloudStoreAccount,
      val sealedCsek: SealedCsek,
      val mobileKeyBackup: CloudBackup,
    ) : State

    /**
     * Indicates that we are currently uploading the backup to the cloud.
     */
    data class UploadingBackupState(
      val cloudAccount: CloudStoreAccount,
      val mobileKeyBackup: CloudBackup,
      val eakBackup: EmergencyAccessKitData,
    ) : State

    /**
     * Indicates that we are currently syncing the backup health status.
     * After that, we are good to exit the repair flow.
     */
    data object SyncingBackupHealthStatus : State

    data class ShowingCustomerSupportUiState(val urlString: String) : State
  }
}
