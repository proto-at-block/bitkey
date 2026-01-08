package build.wallet.statemachine.cloud

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.CloudEventTrackerScreenIdContext.ACCOUNT_CREATION
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.METADATA
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_CLOUD_BACKUP_INITIALIZE
import build.wallet.analytics.v1.Action.ACTION_APP_CLOUD_BACKUP_MISSING
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupError
import build.wallet.cloud.backup.CloudBackupError.RectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.FullAccountCloudBackupCreator
import build.wallet.cloud.backup.UnknownAppDataFoundError
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SekGenerator
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyexitkit.EmergencyExitKitPdfGenerator
import build.wallet.emergencyexitkit.EmergencyExitKitRepository
import build.wallet.emergencyexitkit.EmergencyExitKitRepositoryError.RectifiableCloudError
import build.wallet.feature.flags.SharedCloudBackupsFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logDebug
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.nfc.platform.sealSymmetricKey
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiState.*
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiState.UnrectifiableFailureUiState.*
import build.wallet.statemachine.cloud.RectifiableErrorMessages.Companion.RectifiableErrorCreateFullMessages
import build.wallet.statemachine.core.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.cloud.CloudSignInUiProps
import build.wallet.statemachine.recovery.cloud.CloudSignInUiStateMachine
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

const val SAVING_BACKUP_MESSAGE = "Saving backup..."

@BitkeyInject(ActivityScope::class)
class FullAccountCloudSignInAndBackupUiStateMachineImpl(
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudSignInUiStateMachine: CloudSignInUiStateMachine,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
  private val eventTracker: EventTracker,
  private val rectifiableErrorHandlingUiStateMachine: RectifiableErrorHandlingUiStateMachine,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val sekGenerator: SekGenerator,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val csekDao: CsekDao,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val emergencyExitKitPdfGenerator: EmergencyExitKitPdfGenerator,
  private val emergencyExitKitRepository: EmergencyExitKitRepository,
  private val sharedCloudBackupsFeatureFlag: SharedCloudBackupsFeatureFlag,
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
          cloudBackupRepository.readAllBackups(state.account)
            .onSuccess { backups ->
              when {
                backups.isEmpty() -> {
                  // No backups exist, proceed with creation
                  uiState = CreatingAndSavingBackupUiState(state.account, state.sealedCsek)
                }

                sharedCloudBackupsFeatureFlag.isEnabled() -> {
                  // Shared backups ON: Only conflict if our account ID matches
                  val matchingBackup = backups.find {
                    it.accountId == props.keybox.fullAccountId.serverId
                  }

                  if (matchingBackup != null) {
                    // Found our account ID - show overwrite confirmation
                    handleAppDataFound(
                      state = state,
                      props = props,
                      backup = matchingBackup,
                      setState = { uiState = it }
                    )
                  } else {
                    // Different account IDs - safe to create (won't clobber)
                    uiState = CreatingAndSavingBackupUiState(state.account, state.sealedCsek)
                  }
                }

                else -> {
                  // Shared backups OFF: Existing behavior - any backup is potential conflict
                  handleAppDataFound(
                    state = state,
                    props = props,
                    backup = backups.first(),
                    setState = { uiState = it }
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

                is CloudBackupError.AccountIdMismatched ->
                  uiState = CreatingAndSavingBackupUiState(state.account, state.sealedCsek)
                is UnrectifiableCloudBackupError -> {
                  logError(throwable = cloudBackupError) { "Failed to read cloud backup: $cloudBackupError" }
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
                    uiState = UnrectifiableFailureUiState.CheckingBackupFailure(
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
          title = SAVING_BACKUP_MESSAGE,
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
          setUiState = { uiState = it }
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

      is UnrectifiableFailureUiState.CheckingBackupFailure -> CheckingBackupFailedModel(
        onBackupFailed = props.onBackupFailed,
        presentationStyle = props.presentationStyle,
        errorData = state.errorData
      )

      is UnrectifiableFailureUiState.CreatingBackupFailure -> CreatingBackupFailedModel(
        onBackupFailed = props.onBackupFailed,
        presentationStyle = props.presentationStyle,
        errorData = state.errorData
      )

      is UnrectifiableFailureUiState.UploadingBackupFailure -> UploadingBackupFailedModel(
        onBackupFailed = props.onBackupFailed,
        presentationStyle = props.presentationStyle,
        errorData = state.errorData
      )

      is UnrectifiableFailureUiState.CreatingEmergencyExitKitFailure -> CreatingEmergencyExitKitFailedModel(
        onBackupFailed = props.onBackupFailed,
        presentationStyle = props.presentationStyle,
        errorData = state.errorData
      )

      is UnrectifiableFailureUiState.UploadingEmergencyExitKitFailure -> UploadingEmergencyExitKitFailedModel(
        onBackupFailed = props.onBackupFailed,
        presentationStyle = props.presentationStyle,
        errorData = state.errorData
      )

      is SealingCsekViaNfcUiState ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              commands.sealSymmetricKey(session, state.csek.key)
            },
            onSuccess = { key ->
              csekDao.set(key, state.csek)
              uiState = SigningIntoCloudUiState(key)
            },
            onCancel = { uiState = ShowingBackupInstructionsUiState(false) },
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
            ?: logDebug { "Tapped button before CSEK is generated" }
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
      val csek = sekGenerator.generate()
      onCsekGenerated(csek)
    }
  }

  @Composable
  private fun SigningIntoCloudModel(
    props: FullAccountCloudSignInAndBackupProps,
    onSignedIn: (CloudStoreAccount) -> Unit,
    onSignInFailed: (ErrorData) -> Unit,
  ): BodyModel {
    return cloudSignInUiStateMachine.model(
      props =
        CloudSignInUiProps(
          forceSignOut = !props.isSkipCloudBackupInstructions,
          onSignInFailure = {
            eventTracker.track(action = ACTION_APP_CLOUD_BACKUP_MISSING)
            onSignInFailed(
              ErrorData(
                segment = RecoverySegment.CloudBackup.FullAccount.SignIn,
                cause = it,
                actionDescription = "Signing into cloud account failed"
              )
            )
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
    onBack: () -> Unit,
    setUiState: (FullAccountCloudSignInAndBackupUiState) -> Unit,
  ): BodyModel {
    CreateAndSaveBackupEffect(
      props = props,
      state = state,
      onRectifiableError = onRectifiableError,
      sealedCsek = sealedCsek,
      setUiState = setUiState
    )

    return LoadingBodyModel(
      title = SAVING_BACKUP_MESSAGE,
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
    setUiState: (FullAccountCloudSignInAndBackupUiState) -> Unit,
  ) {
    LaunchedEffect("create-and-save-backup") {
      coroutineBinding {
        // Create the cloud backup.
        val cloudBackup =
          fullAccountCloudBackupCreator
            .create(
              keybox = props.keybox,
              sealedCsek = sealedCsek
            )
            .logFailure { "Error creating cloud backup" }
            .onFailure {
              setUiState(
                UnrectifiableFailureUiState.CreatingBackupFailure(
                  ErrorData(
                    segment = RecoverySegment.CloudBackup.FullAccount.Creation,
                    cause = it,
                    actionDescription = "Creating cloud backup"
                  )
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
          .onSuccess {
            logInfo {
              "Cloud backup uploaded via FullAccountCloudSignInAndBackupUiStateMachine"
            }
          }
          .logFailure { "Error saving cloud backup to cloud storage" }
          .onFailure { cloudBackupFailure ->
            val errorData = ErrorData(
              segment = RecoverySegment.CloudBackup.FullAccount.Upload,
              cause = cloudBackupFailure,
              actionDescription = "Uploading full account backup to cloud"
            )
            if (cloudBackupFailure is RectifiableCloudBackupError) {
              onRectifiableError(
                cloudBackupFailure,
                errorData
              )
            } else {
              setUiState(UnrectifiableFailureUiState.UploadingBackupFailure(errorData))
            }
          }
          .bind()

        // Create the Emergency Exit Kit.
        val emergencyExitKitData =
          emergencyExitKitPdfGenerator
            .generate(
              keybox = props.keybox,
              sealedCsek = sealedCsek
            )
            .logFailure { "Error creating Emergency Exit Kit data" }
            .onFailure {
              setUiState(
                UnrectifiableFailureUiState.CreatingEmergencyExitKitFailure(
                  ErrorData(
                    segment = RecoverySegment.EmergencyExit.Creation,
                    cause = it,
                    actionDescription = "Creating Emergency Exit Kit"
                  )
                )
              )
            }
            .bind()

        // Save the Emergency Exit Kit.
        emergencyExitKitRepository
          .write(
            account = state.cloudStoreAccount,
            emergencyExitKitData = emergencyExitKitData
          )
          .logFailure { "Error saving Emergency Exit Kit to cloud file store" }
          .onFailure { writeFailure ->
            val errorData = ErrorData(
              segment = RecoverySegment.EmergencyExit.Upload,
              cause = writeFailure,
              actionDescription = "Uploading Emergency Exit Kit to cloud"
            )
            if (writeFailure is RectifiableCloudError) {
              onRectifiableError(
                writeFailure.toRectifiableCloudBackupError,
                errorData
              )
            } else {
              setUiState(UnrectifiableFailureUiState.UploadingEmergencyExitKitFailure(errorData))
            }
          }
          .bind()

        props.onBackupSaved()
      }
    }
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
   * If we don't previously have a signed CSEK, we sign here in this state.
   *
   * @property csek - The CSEK to sign
   */
  data class SealingCsekViaNfcUiState(
    val csek: Csek,
  ) : FullAccountCloudSignInAndBackupUiState()

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
   * Base class for all unrectifiable failure states during the backup process.
   */
  sealed class UnrectifiableFailureUiState : FullAccountCloudSignInAndBackupUiState() {
    abstract val errorData: ErrorData

    /**
     * Error during backup checking process.
     */
    data class CheckingBackupFailure(
      override val errorData: ErrorData,
    ) : UnrectifiableFailureUiState()

    /**
     * Error during backup creation process.
     */
    data class CreatingBackupFailure(
      override val errorData: ErrorData,
    ) : UnrectifiableFailureUiState()

    /**
     * Error during backup upload process.
     */
    data class UploadingBackupFailure(
      override val errorData: ErrorData,
    ) : UnrectifiableFailureUiState()

    /**
     * Error during Emergency Exit Kit creation process.
     */
    data class CreatingEmergencyExitKitFailure(
      override val errorData: ErrorData,
    ) : UnrectifiableFailureUiState()

    /**
     * Error during Emergency Exit Kit upload process.
     */
    data class UploadingEmergencyExitKitFailure(
      override val errorData: ErrorData,
    ) : UnrectifiableFailureUiState()
  }
}
