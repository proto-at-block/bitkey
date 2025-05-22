package build.wallet.statemachine.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.backup.CloudBackupError
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine

interface RectifiableErrorHandlingUiStateMachine : StateMachine<RectifiableErrorHandlingProps, ScreenModel>

data class RectifiableErrorHandlingProps(
  val messages: RectifiableErrorMessages,
  val rectifiableError: CloudBackupError.RectifiableCloudBackupError,
  val cloudStoreAccount: CloudStoreAccount,
  val onFailure: (Error?) -> Unit,
  val onReturn: () -> Unit,
  val screenId: CloudEventTrackerScreenId,
  val presentationStyle: ScreenPresentationStyle,
  // TODO(BKR-1120): Make this non-nullable
  val errorData: ErrorData?,
)

data class RectifiableErrorMessages(
  val title: String,
  val subline: String,
) {
  companion object {
    val RectifiableErrorAccessMessages =
      RectifiableErrorMessages(
        title = "There was a problem accessing your cloud backup for your App Key",
        subline = "Please try connecting to a cloud account and accessing your backup again. Make sure Bitkey’s permissions to write to your cloud are enabled."
      )
    val RectifiableErrorCreateFullMessages =
      RectifiableErrorMessages(
        title = "There was a problem creating a cloud backup for your App Key",
        subline = "Please try connecting to a cloud account and creating a backup again. Make sure Bitkey’s permissions to write to your cloud are enabled."
      )
    val RectifiableErrorCreateLiteMessages =
      RectifiableErrorCreateFullMessages.copy(
        title = "There was a problem creating a cloud backup for your account"
      )
  }
}
