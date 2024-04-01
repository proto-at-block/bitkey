package build.wallet.statemachine.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.CloudEventTrackerScreenIdContext.ACCOUNT_CREATION
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.METADATA
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_CLOUD_BACKUP_INITIALIZE
import build.wallet.analytics.v1.Action.ACTION_APP_CLOUD_BACKUP_MISSING
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupError.RectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.FullAccountCloudBackupCreator
import build.wallet.cloud.backup.UnknownAppDataFoundError
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.csek.CsekGenerator
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.emergencyaccesskit.EmergencyAccessKitPdfGenerator
import build.wallet.emergencyaccesskit.EmergencyAccessKitRepository
import build.wallet.emergencyaccesskit.EmergencyAccessKitRepositoryError.RectifiableCloudError
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiState.CheckingCloudBackupUiState
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiState.CloudSignInFailedUiState
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiState.CreatingAndSavingBackupUiState
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiState.FailureUiState
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiState.RectifiableFailureUiState
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiState.SealingCsekViaNfcUiState
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiState.ShowingBackupInstructionsUiState
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiState.ShowingBackupLearnMoreUiState
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiState.ShowingCustomerSupportUiState
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiState.SigningIntoCloudUiState
import build.wallet.statemachine.cloud.RectifiableErrorMessages.Companion.RectifiableErrorCreateFullMessages
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.cloud.CloudSignInUiProps
import build.wallet.statemachine.recovery.cloud.CloudSignInUiStateMachine
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

const val SAVING_BACKUP_MESSAGE = "Saving backup..."

class FullAccountCloudSignInAndBackupUiStateMachineImpl(
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudSignInUiStateMachine: CloudSignInUiStateMachine,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
  private val eventTracker: EventTracker,
  private val rectifiableErrorHandlingUiStateMachine: RectifiableErrorHandlingUiStateMachine,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val csekGenerator: CsekGenerator,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val csekDao: CsekDao,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val emergencyAccessKitPdfGenerator: EmergencyAccessKitPdfGenerator,
  private val emergencyAccessKitRepository: EmergencyAccessKitRepository,
) : FullAccountCloudSignInAndBackupUiStateMachine {
  @Composable
  override fun model(props: FullAccountCloudSignInAndBackupProps): ScreenModel {
    var uiState: FullAccountCloudSignInAndBackupUiState by remember {
      mutableStateOf(
        if (props.isSkipCloudBackupInstructions && props.sealedCsek != null) {
          SigningIntoCloudUiState(props.sealedCsek)
        } else {
          ShowingBackupInstructionsUiState(false)
        }
      )
    }

    return when (val state = uiState) {
      is ShowingBackupInstructionsUiState ->
        ShowingBackupInstructionsModel(
          requiresHardware = props.sealedCsek == null,
          sealedCsek = props.sealedCsek,
          goToSealingCsek = { csek ->
            uiState = SealingCsekViaNfcUiState(csek)
          },
          goToSigningIntoCloud = { sealedCsek ->
            uiState = SigningIntoCloudUiState(sealedCsek = sealedCsek)
          },
          goToLearnMore = {
            uiState =
              ShowingBackupLearnMoreUiState(
                "https://support.bitkey.world/hc/en-us/articles/18842210239764-What-is-Cloud-Recovery-and-how-does-it-work"
              )
          }
        ).asScreen(props.presentationStyle)
      is ShowingCustomerSupportUiState ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = state.urlString,
              onClose = {
                uiState = CloudSignInFailedUiState(sealedCsek = state.sealedCsek)
              }
            )
          }
        ).asModalScreen()
      is ShowingBackupLearnMoreUiState ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = state.urlString,
              onClose = {
                uiState = ShowingBackupInstructionsUiState(false)
              }
            )
          }
        ).asModalScreen()

      is SigningIntoCloudUiState ->
        SigningIntoCloudModel(
          props = props,
          onSignedIn = {
            uiState = CheckingCloudBackupUiState(it, state.sealedCsek)
          },
          onSignInFailed = {
            uiState = CloudSignInFailedUiState(sealedCsek = state.sealedCsek)
          }
        ).asScreen(props.presentationStyle)

      is CloudSignInFailedUiState ->
        CloudSignInFailedScreenModel(
          onTryAgain = {
            uiState = SigningIntoCloudUiState(sealedCsek = state.sealedCsek)
          },
          onBack = {
            uiState = ShowingBackupInstructionsUiState(false)
          },
          onContactSupport = {
            uiState = ShowingCustomerSupportUiState(
              urlString = "https://support.bitkey.world/hc/en-us",
              sealedCsek = state.sealedCsek
            )
          },
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
        ).asScreen(props.presentationStyle)

      is CheckingCloudBackupUiState -> {
        LaunchedEffect("check cloud account for backup") {
          cloudBackupRepository.readBackup(state.account)
            .onSuccess { backup ->
              when (backup) {
                null -> uiState = CreatingAndSavingBackupUiState(state.account, state.sealedCsek)
                else -> {
                  handleAppDataFound(
                    state = state,
                    props = props,
                    backup = backup,
                    setState = {
                      uiState = it
                    }
                  )
                }
              }
            }
            .onFailure { cloudBackupError ->
              when (cloudBackupError) {
                is RectifiableCloudBackupError -> {
                  uiState =
                    RectifiableFailureUiState(
                      cloudStoreAccount = state.account,
                      sealedCsek = state.sealedCsek,
                      rectifiableCloudBackupError = cloudBackupError,
                      errorData = ErrorData(
                        segment = RecoverySegment.CloudBackup.FullAccount.Creation,
                        cause = cloudBackupError,
                        actionDescription = "Checking cloud backup"
                      )
                    )
                }

                is UnrectifiableCloudBackupError -> {
                  log(LogLevel.Warn) { "Failed to read cloud backup: $cloudBackupError" }
                  if (cloudBackupError.cause is UnknownAppDataFoundError) {
                    // If unknown app data was found, give the option to overwrite.
                    handleAppDataFound(
                      state = state,
                      props = props,
                      backup = null,
                      setState = {
                        uiState = it
                      }
                    )
                  } else {
                    uiState = FailureUiState(
                      ErrorData(
                        segment = RecoverySegment.CloudBackup.FullAccount.Creation,
                        cause = cloudBackupError,
                        actionDescription = "Checking cloud backup"
                      )
                    )
                  }
                }
              }
            }
        }

        LoadingBodyModel(
          message = SAVING_BACKUP_MESSAGE,
          onBack = {
            props.onBackupFailed(null)
          },
          id = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_CHECK_FOR_EXISTING
        ).asRootScreen()
      }

      is CreatingAndSavingBackupUiState ->
        CreatingAndSavingBackupModel(
          props = props,
          state = state,
          sealedCsek = state.sealedCsek,
          onBack = {
            uiState = ShowingBackupInstructionsUiState(false)
          },
          onRectifiableError = { rectifiableCloudBackupError, errorData ->
            uiState =
              RectifiableFailureUiState(
                cloudStoreAccount = state.cloudStoreAccount,
                rectifiableCloudBackupError = rectifiableCloudBackupError,
                sealedCsek = state.sealedCsek,
                errorData = errorData
              )
          },
          onFailure = {
            uiState = FailureUiState(it)
          }
        ).asScreen(props.presentationStyle)

      is RectifiableFailureUiState ->
        return rectifiableErrorHandlingUiStateMachine.model(
          props =
            RectifiableErrorHandlingProps(
              messages = RectifiableErrorCreateFullMessages,
              cloudStoreAccount = state.cloudStoreAccount,
              rectifiableError = state.rectifiableCloudBackupError,
              screenId = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT_RECTIFIABLE,
              onFailure = props.onBackupFailed,
              onReturn = {
                uiState =
                  CreatingAndSavingBackupUiState(
                    cloudStoreAccount = state.cloudStoreAccount,
                    sealedCsek = state.sealedCsek
                  )
              },
              presentationStyle = props.presentationStyle,
              errorData = state.errorData
            )
        )

      is FailureUiState -> FailedToCreateBackupModel(
        props = props,
        errorData = state.errorData
      )

      is SealingCsekViaNfcUiState ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              commands.sealKey(session, state.csek)
            },
            onSuccess = { key ->
              csekDao.set(key, state.csek)
              uiState = SigningIntoCloudUiState(key)
            },
            onCancel = { uiState = ShowingBackupInstructionsUiState(false) },
            isHardwareFake = props.keybox.config.isHardwareFake,
            needsAuthentication = false,
            screenPresentationStyle = props.presentationStyle,
            eventTrackerContext = METADATA
          )
        )
    }
  }

  private fun handleAppDataFound(
    state: CheckingCloudBackupUiState,
    props: FullAccountCloudSignInAndBackupProps,
    backup: CloudBackup?,
    setState: (FullAccountCloudSignInAndBackupUiState) -> Unit,
  ) {
    val proceed = {
      setState(CreatingAndSavingBackupUiState(state.account, state.sealedCsek))
    }
    props.onExistingAppDataFound?.invoke(backup, proceed) ?: proceed()
  }

  @Composable
  private fun ShowingBackupInstructionsModel(
    requiresHardware: Boolean,
    sealedCsek: SealedCsek?,
    goToSealingCsek: (csek: Csek) -> Unit,
    goToSigningIntoCloud: (sealedCsek: SealedCsek) -> Unit,
    goToLearnMore: () -> Unit,
  ): BodyModel {
    var isGeneratingCsek by remember { mutableStateOf(requiresHardware) }
    var csek: Csek? by remember { mutableStateOf(null) }
    if (isGeneratingCsek) {
      GenerateCsek {
        csek = it
        isGeneratingCsek = false
      }
    }

    return SaveBackupInstructionsBodyModel(
      requiresHardware = requiresHardware,
      isLoading = isGeneratingCsek,
      onBackupClick = {
        when (sealedCsek) {
          null -> csek?.let { goToSealingCsek(it) }
            ?: log { "Tapped button before CSEK is generated" }
          else -> goToSigningIntoCloud(sealedCsek)
        }
      },
      onLearnMoreClick = {
        goToLearnMore()
      },
      devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
    )
  }

  @Composable
  private fun GenerateCsek(onCsekGenerated: (csek: Csek) -> Unit) {
    LaunchedEffect("generating-csek") {
      val csek = csekGenerator.generate()
      onCsekGenerated(csek)
    }
  }

  @Composable
  private fun SigningIntoCloudModel(
    props: FullAccountCloudSignInAndBackupProps,
    onSignedIn: (CloudStoreAccount) -> Unit,
    onSignInFailed: () -> Unit,
  ): BodyModel {
    return cloudSignInUiStateMachine.model(
      props =
        CloudSignInUiProps(
          forceSignOut = !props.isSkipCloudBackupInstructions,
          onSignInFailure = {
            eventTracker.track(action = ACTION_APP_CLOUD_BACKUP_MISSING)
            onSignInFailed()
          },
          onSignedIn = { account ->
            eventTracker.track(action = ACTION_APP_CLOUD_BACKUP_INITIALIZE)
            onSignedIn(account)
          },
          eventTrackerContext = ACCOUNT_CREATION
        )
    )
  }

  @Composable
  private fun CreatingAndSavingBackupModel(
    props: FullAccountCloudSignInAndBackupProps,
    state: CreatingAndSavingBackupUiState,
    sealedCsek: SealedCsek,
    onRectifiableError: (RectifiableCloudBackupError, ErrorData) -> Unit,
    onFailure: (ErrorData) -> Unit,
    onBack: () -> Unit,
  ): BodyModel {
    CreateAndSaveBackupEffect(
      props = props,
      state = state,
      onRectifiableError = onRectifiableError,
      sealedCsek = sealedCsek,
      onFailure = onFailure
    )

    return LoadingBodyModel(
      message = SAVING_BACKUP_MESSAGE,
      onBack = onBack,
      id = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_LOADING
    )
  }

  @Composable
  private fun CreateAndSaveBackupEffect(
    props: FullAccountCloudSignInAndBackupProps,
    state: CreatingAndSavingBackupUiState,
    sealedCsek: SealedCsek,
    onRectifiableError: (RectifiableCloudBackupError, ErrorData) -> Unit,
    onFailure: (ErrorData) -> Unit,
  ) {
    LaunchedEffect("create-and-save-backup") {
      binding {
        // Create the cloud backup.
        val cloudBackup =
          fullAccountCloudBackupCreator
            .create(
              keybox = props.keybox,
              sealedCsek = sealedCsek,
              trustedContacts = props.trustedContacts
            )
            .logFailure { "Error creating cloud backup" }
            .onFailure {
              onFailure(
                ErrorData(
                  segment = RecoverySegment.CloudBackup.FullAccount.Creation,
                  cause = it,
                  actionDescription = "Creating cloud backup"
                )
              )
            }
            .bind()

        // Save the cloud backup.
        cloudBackupRepository
          .writeBackup(
            accountId = props.keybox.fullAccountId,
            cloudStoreAccount = state.cloudStoreAccount,
            backup = cloudBackup,
            requireAuthRefresh = props.requireAuthRefreshForCloudBackup
          )
          .logFailure { "Error saving cloud backup to cloud storage" }
          .onFailure { cloudBackupFailure ->
            val errorData = ErrorData(
              segment = RecoverySegment.CloudBackup.FullAccount.Upload,
              cause = cloudBackupFailure,
              actionDescription = "Writing backup to cloud storage"
            )
            if (cloudBackupFailure is RectifiableCloudBackupError) {
              onRectifiableError(
                cloudBackupFailure,
                errorData
              )
            } else {
              onFailure(errorData)
            }
          }
          .bind()

        // Create the emergency access kit.
        val emergencyAccessKitData =
          emergencyAccessKitPdfGenerator
            .generate(
              keybox = props.keybox,
              sealedCsek = sealedCsek
            )
            .logFailure { "Error creating emergency access kit data" }
            .onFailure {
              onFailure(
                ErrorData(
                  segment = RecoverySegment.EmergencyAccess.Creation,
                  cause = it,
                  actionDescription = "Creating emergency access kit"
                )
              )
            }
            .bind()

        // Save the emergency access kit.
        emergencyAccessKitRepository
          .write(
            account = state.cloudStoreAccount,
            emergencyAccessKitData = emergencyAccessKitData
          )
          .logFailure { "Error saving emergency access kit to cloud file store" }
          .onFailure { writeFailure ->
            val errorData = ErrorData(
              segment = RecoverySegment.EmergencyAccess.Upload,
              cause = writeFailure,
              actionDescription = "Writing emergency access kit to cloud file store"
            )
            if (writeFailure is RectifiableCloudError) {
              onRectifiableError(
                writeFailure.toRectifiableCloudBackupError,
                errorData
              )
            } else {
              onFailure(errorData)
            }
          }
          .bind()

        props.onBackupSaved()
      }
    }
  }

  @Composable
  fun FailedToCreateBackupModel(
    props: FullAccountCloudSignInAndBackupProps,
    errorData: ErrorData,
  ): ScreenModel {
    return ErrorFormBodyModel(
      title = "We were unable to create backup",
      subline = "Please try again later.",
      primaryButton = ButtonDataModel(text = "Done", onClick = {
        props.onBackupFailed(errorData.cause)
      }),
      eventTrackerScreenId = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT,
      errorData = errorData
    ).asScreen(props.presentationStyle)
  }
}

private sealed class FullAccountCloudSignInAndBackupUiState {
  /**
   * Showing instructions that we are about to start backing up to cloud.
   */
  data class ShowingBackupInstructionsUiState(
    val generatingCsek: Boolean = false,
  ) : FullAccountCloudSignInAndBackupUiState()

  data class ShowingCustomerSupportUiState(
    val urlString: String,
    val sealedCsek: SealedCsek,
  ) : FullAccountCloudSignInAndBackupUiState()

  /**
   * Showing cloud backup 'learn more' content.
   */
  data class ShowingBackupLearnMoreUiState(
    val urlString: String,
  ) : FullAccountCloudSignInAndBackupUiState()

  /**
   * Currently signing into a cloud account.
   *
   * @property sealedCsek - The signed CSEK, null when there isn't one available.
   */
  data class SigningIntoCloudUiState(
    val sealedCsek: SealedCsek,
  ) : FullAccountCloudSignInAndBackupUiState()

  /**
   * State entered when there was a failure signing into cloud account
   *
   * @property sealedCsek - The signed CSEK to backup
   */
  data class CloudSignInFailedUiState(
    val sealedCsek: SealedCsek,
  ) : FullAccountCloudSignInAndBackupUiState()

  /**
   * Checking cloud backup to see if one already exists
   */
  data class CheckingCloudBackupUiState(
    val account: CloudStoreAccount,
    val sealedCsek: SealedCsek,
  ) : FullAccountCloudSignInAndBackupUiState()

  /**
   * In process of creating and saving the backup.
   */
  data class CreatingAndSavingBackupUiState(
    val cloudStoreAccount: CloudStoreAccount,
    val sealedCsek: SealedCsek,
  ) : FullAccountCloudSignInAndBackupUiState()

  /**
   * Error during the process.
   */
  data class FailureUiState(val errorData: ErrorData) : FullAccountCloudSignInAndBackupUiState()

  /**
   * Explaining error that may be fixable.
   */
  data class RectifiableFailureUiState(
    val cloudStoreAccount: CloudStoreAccount,
    val rectifiableCloudBackupError: RectifiableCloudBackupError,
    val sealedCsek: SealedCsek,
    val errorData: ErrorData,
  ) : FullAccountCloudSignInAndBackupUiState()

  /**
   * If we don't previously have a signed CSEK, we sign here in this state.
   *
   * @property csek - The CSEK to sign
   */
  data class SealingCsekViaNfcUiState(
    val csek: Csek,
  ) : FullAccountCloudSignInAndBackupUiState()
}
