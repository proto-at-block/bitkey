package build.wallet.statemachine.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.CloudEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT_RECTIFIABLE
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_LOADING
import build.wallet.analytics.v1.Action
import build.wallet.cloud.backup.CloudBackupError.RectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupState.CloudSignInFailedState
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupState.CreatingAndSavingBackupState
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupState.NonRectifiableFailureState
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupState.RectifiableFailureState
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupState.SigningIntoCloudState
import build.wallet.statemachine.cloud.RectifiableErrorMessages.Companion.RectifiableErrorCreateLiteMessages
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.cloud.CloudSignInUiProps
import build.wallet.statemachine.recovery.cloud.CloudSignInUiStateMachine
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class LiteAccountCloudSignInAndBackupUiStateMachineImpl(
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudSignInUiStateMachine: CloudSignInUiStateMachine,
  private val liteAccountCloudBackupCreator: LiteAccountCloudBackupCreator,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val eventTracker: EventTracker,
  private val rectifiableErrorHandlingUiStateMachine: RectifiableErrorHandlingUiStateMachine,
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
            state = CloudSignInFailedState
          }
        )
      }
      CloudSignInFailedState ->
        CloudSignInFailedScreenModel(
          onTryAgain = {
            state = SigningIntoCloudState
          },
          onBack = props.onBackupFailed,
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
        ).asScreen(props.presentationStyle)
      is CreatingAndSavingBackupState -> {
        LaunchedEffect("create-lite-account-cloud-backup") {
          val cloudBackup =
            liteAccountCloudBackupCreator
              .create(props.liteAccount)
              .getOrElse {
                state = NonRectifiableFailureState
                return@LaunchedEffect
              }

          cloudBackupRepository.writeBackup(
            props.liteAccount.accountId,
            uiState.cloudStoreAccount,
            backup = cloudBackup
          )
            .onFailure { error ->
              state =
                when (error) {
                  is RectifiableCloudBackupError -> {
                    RectifiableFailureState(
                      cloudStoreAccount = uiState.cloudStoreAccount,
                      rectifiableCloudBackupError = error
                    )
                  }
                  is UnrectifiableCloudBackupError -> {
                    NonRectifiableFailureState
                  }
                }
            }
            .onSuccess {
              props.onBackupSaved()
            }
        }
        return LoadingBodyModel(
          message = "Saving backup...",
          onBack = props.onBackupFailed,
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
              presentationStyle = props.presentationStyle
            )
        )
      NonRectifiableFailureState -> {
        return ErrorFormBodyModel(
          title = "We were unable to create backup",
          subline = "Please try again later.",
          primaryButton = ButtonDataModel(text = "Done", onClick = props.onBackupFailed),
          eventTrackerScreenId = SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT
        ).asScreen(props.presentationStyle)
      }
    }
  }

  @Composable
  private fun SigningIntoCloudModel(
    props: LiteAccountCloudSignInAndBackupProps,
    onSignedIn: (CloudStoreAccount) -> Unit,
    onSignInFailed: () -> Unit,
  ): ScreenModel {
    return cloudSignInUiStateMachine.model(
      props =
        CloudSignInUiProps(
          forceSignOut = false,
          onSignInFailure = {
            eventTracker.track(action = Action.ACTION_APP_CLOUD_BACKUP_MISSING)
            onSignInFailed()
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
  data object CloudSignInFailedState : LiteAccountCloudSignInAndBackupState()

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
  ) : LiteAccountCloudSignInAndBackupState()

  /**
   * Error during the process
   */
  data object NonRectifiableFailureState : LiteAccountCloudSignInAndBackupState()
}
