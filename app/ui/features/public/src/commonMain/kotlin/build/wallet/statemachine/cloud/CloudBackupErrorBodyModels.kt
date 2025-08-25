package build.wallet.statemachine.cloud

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle

@Composable
fun CheckingBackupFailedModel(
  onBackupFailed: (Throwable?) -> Unit,
  presentationStyle: ScreenPresentationStyle,
  errorData: ErrorData,
): ScreenModel {
  return ErrorFormBodyModel(
    title = "We couldn't access your cloud backup",
    subline = "Check your connection and try again.",
    primaryButton = ButtonDataModel(text = "Done", onClick = {
      onBackupFailed(errorData.cause)
    }),
    eventTrackerScreenId = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT,
    errorData = errorData
  ).asScreen(presentationStyle)
}

@Composable
fun CreatingBackupFailedModel(
  onBackupFailed: (Throwable?) -> Unit,
  presentationStyle: ScreenPresentationStyle,
  errorData: ErrorData,
): ScreenModel {
  return ErrorFormBodyModel(
    title = "We couldn't create your cloud backup",
    subline = "Please try again.",
    primaryButton = ButtonDataModel(text = "Done", onClick = {
      onBackupFailed(errorData.cause)
    }),
    eventTrackerScreenId = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT,
    errorData = errorData
  ).asScreen(presentationStyle)
}

@Composable
fun UploadingBackupFailedModel(
  onBackupFailed: (Throwable?) -> Unit,
  presentationStyle: ScreenPresentationStyle,
  errorData: ErrorData,
): ScreenModel {
  return ErrorFormBodyModel(
    title = "We couldn't save your cloud backup",
    subline = "Please check your connection and try again.",
    primaryButton = ButtonDataModel(text = "Done", onClick = {
      onBackupFailed(errorData.cause)
    }),
    eventTrackerScreenId = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT,
    errorData = errorData
  ).asScreen(presentationStyle)
}

@Composable
fun CreatingEmergencyExitKitFailedModel(
  onBackupFailed: (Throwable?) -> Unit,
  presentationStyle: ScreenPresentationStyle,
  errorData: ErrorData,
): ScreenModel {
  return ErrorFormBodyModel(
    title = "We couldn't create your Emergency Exit Kit",
    subline = "Please try again.",
    primaryButton = ButtonDataModel(text = "Done", onClick = {
      onBackupFailed(errorData.cause)
    }),
    eventTrackerScreenId = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT,
    errorData = errorData
  ).asScreen(presentationStyle)
}

@Composable
fun UploadingEmergencyExitKitFailedModel(
  onBackupFailed: (Throwable?) -> Unit,
  presentationStyle: ScreenPresentationStyle,
  errorData: ErrorData,
): ScreenModel {
  return ErrorFormBodyModel(
    title = "We couldn't save your Emergency Exit Kit",
    subline = "Please check your connection and try again.",
    primaryButton = ButtonDataModel(text = "Done", onClick = {
      onBackupFailed(errorData.cause)
    }),
    eventTrackerScreenId = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT,
    errorData = errorData
  ).asScreen(presentationStyle)
}
