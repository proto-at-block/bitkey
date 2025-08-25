package build.wallet.statemachine.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.CloudEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT_RECTIFIABLE
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_LOADING
import build.wallet.analytics.v1.Action
import build.wallet.cloud.backup.CloudBackupError.RectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logInfo
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupState.CloudSignInFailedState
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupState.CreatingAndSavingBackupState
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupState.RectifiableFailureState
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupState.ShowingCustomerSupportUiState
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupState.SigningIntoCloudState
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupState.UnrectifiableFailureState
import build.wallet.statemachine.cloud.RectifiableErrorMessages.Companion.RectifiableErrorCreateLiteMessages
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.cloud.CloudSignInUiProps
import build.wallet.statemachine.recovery.cloud.CloudSignInUiStateMachine
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class LiteAccountCloudSignInAndBackupUiStateMachineImpl(
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudSignInUiStateMachine: CloudSignInUiStateMachine,
  private val liteAccountCloudBackupCreator: LiteAccountCloudBackupCreator,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val eventTracker: EventTracker,
  private val rectifiableErrorHandlingUiStateMachine: RectifiableErrorHandlingUiStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : LiteAccountCloudSignInAndBackupUiStateMachine {
  @Composable
  override fun model(props: LiteAccountCloudSignInAndBackupProps): ScreenModel {
    var state: LiteAccountCloudSignInAndBackupState by remember {
      mutableStateOf(SigningIntoCloudState)
    }
    return when (val uiState = state) {
      SigningIntoCloudState -> {
        SigningIntoCloudModel(
          props = props,
          onSignedIn = {
            state = CreatingAndSavingBackupState(it)
          },
          onSignInFailed = {
            state = CloudSignInFailedState(it)
          }
        )
      }
      is ShowingCustomerSupportUiState ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = uiState.urlString,
              onClose = {
                state = CloudSignInFailedState(null)
              }
            )
          }
        ).asModalScreen()
      is CloudSignInFailedState ->
        CloudSignInFailedScreenModel(
          onContactSupport = {
            state = ShowingCustomerSupportUiState(
              urlString = "https://support.bitkey.world/hc/en-us"
            )
          },
          onTryAgain = {
            state = SigningIntoCloudState
          },
          onBack = {
            props.onBackupFailed(uiState.cause)
          },
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
        ).asScreen(props.presentationStyle)
      is CreatingAndSavingBackupState -> {
        LaunchedEffect("create-lite-account-cloud-backup") {
          val cloudBackup =
            liteAccountCloudBackupCreator
              .create(props.liteAccount)
              .getOrElse {
                state = UnrectifiableFailureState.CreatingLiteAccountBackupFailure(
                  errorData = ErrorData(
                    segment = RecoverySegment.CloudBackup.LiteAccount.Creation,
                    cause = it,
                    actionDescription = "Creating lite account cloud backup"
                  )
                )
                return@LaunchedEffect
              }

          cloudBackupRepository.writeBackup(
            props.liteAccount.accountId,
            uiState.cloudStoreAccount,
            backup = cloudBackup,
            requireAuthRefresh = true
          )
            .onFailure { error ->
              val errorData = ErrorData(
                segment = RecoverySegment.CloudBackup.LiteAccount.Upload,
                cause = error,
                actionDescription = "Uploading lite account backup to cloud"
              )
              state =
                when (error) {
                  is RectifiableCloudBackupError -> {
                    RectifiableFailureState(
                      cloudStoreAccount = uiState.cloudStoreAccount,
                      rectifiableCloudBackupError = error,
                      errorData = errorData
                    )
                  }
                  is UnrectifiableCloudBackupError -> {
                    UnrectifiableFailureState.UploadingLiteAccountBackupFailure(
                      errorData = errorData
                    )
                  }
                }
            }
            .onSuccess {
              logInfo {
                "Cloud backup uploaded via LiteAccountCloudSignInAndBackupUiStateMachine"
              }
              props.onBackupSaved()
            }
        }
        return LoadingBodyModel(
          message = "Saving backup...",
          onBack = {
            props.onBackupFailed(null)
          },
          id = SAVE_CLOUD_BACKUP_LOADING
        ).asScreen(props.presentationStyle)
      }
      is RectifiableFailureState ->
        rectifiableErrorHandlingUiStateMachine.model(
          props =
            RectifiableErrorHandlingProps(
              messages = RectifiableErrorCreateLiteMessages,
              cloudStoreAccount = uiState.cloudStoreAccount,
              rectifiableError = uiState.rectifiableCloudBackupError,
              screenId = SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT_RECTIFIABLE,
              onFailure = props.onBackupFailed,
              onReturn = {
                state =
                  CreatingAndSavingBackupState(
                    cloudStoreAccount = uiState.cloudStoreAccount
                  )
              },
              presentationStyle = props.presentationStyle,
              errorData = uiState.errorData
            )
        )
      is UnrectifiableFailureState.CreatingLiteAccountBackupFailure -> {
        return CreatingBackupFailedModel(
          onBackupFailed = props.onBackupFailed,
          presentationStyle = props.presentationStyle,
          errorData = uiState.errorData
        )
      }
      is UnrectifiableFailureState.UploadingLiteAccountBackupFailure -> {
        return UploadingBackupFailedModel(
          onBackupFailed = props.onBackupFailed,
          presentationStyle = props.presentationStyle,
          errorData = uiState.errorData
        )
      }
    }
  }

  @Composable
  private fun SigningIntoCloudModel(
    props: LiteAccountCloudSignInAndBackupProps,
    onSignedIn: (CloudStoreAccount) -> Unit,
    onSignInFailed: (Error) -> Unit,
  ): ScreenModel {
    return cloudSignInUiStateMachine.model(
      props =
        CloudSignInUiProps(
          forceSignOut = false,
          onSignInFailure = {
            eventTracker.track(action = Action.ACTION_APP_CLOUD_BACKUP_MISSING)
            onSignInFailed(it)
          },
          onSignedIn = { account ->
            eventTracker.track(action = Action.ACTION_APP_CLOUD_BACKUP_INITIALIZE)
            onSignedIn(account)
          },
          eventTrackerContext = CloudEventTrackerScreenIdContext.ACCOUNT_CREATION
        )
    ).asScreen(props.presentationStyle)
  }
}

private sealed class LiteAccountCloudSignInAndBackupState {
  /**
   * Currently signing into a cloud account.
   */
  data object SigningIntoCloudState : LiteAccountCloudSignInAndBackupState()

  /**
   * State entered when there was a failure signing into cloud account
   */
  data class CloudSignInFailedState(
    val cause: Error?,
  ) : LiteAccountCloudSignInAndBackupState()

  /**
   * In process of creating and saving the backup.
   */
  data class CreatingAndSavingBackupState(
    val cloudStoreAccount: CloudStoreAccount,
  ) : LiteAccountCloudSignInAndBackupState()

  /**
   * Explaining error that may be fixable
   */
  data class RectifiableFailureState(
    val cloudStoreAccount: CloudStoreAccount,
    val rectifiableCloudBackupError: RectifiableCloudBackupError,
    val errorData: ErrorData,
  ) : LiteAccountCloudSignInAndBackupState()

  /**
   * Base class for all unrectifiable failure states during the backup process.
   */
  sealed class UnrectifiableFailureState : LiteAccountCloudSignInAndBackupState() {
    abstract val errorData: ErrorData

    /**
     * Error during lite account backup creation process.
     */
    data class CreatingLiteAccountBackupFailure(
      override val errorData: ErrorData,
    ) : UnrectifiableFailureState()

    /**
     * Error during lite account backup upload process.
     */
    data class UploadingLiteAccountBackupFailure(
      override val errorData: ErrorData,
    ) : UnrectifiableFailureState()
  }

  data class ShowingCustomerSupportUiState(
    val urlString: String,
  ) : LiteAccountCloudSignInAndBackupState()
}
