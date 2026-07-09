package build.wallet.statemachine.fwup

import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.nfc.NfcException
import build.wallet.platform.device.DeviceInfo
import build.wallet.platform.device.DevicePlatform.IOS
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBottomSheetModel
import build.wallet.statemachine.core.SheetModel

/**
 * @param error The NfcException that caused the failure, used for contextual error messaging.
 * @param deviceInfo Provides information about the phone in order to customize messaging.
 * @param wasInProgress Whether or not the FWUP was in progress before the error occurred.
 */
fun FwupUpdateErrorModel(
  error: NfcException,
  deviceInfo: DeviceInfo,
  wasInProgress: Boolean,
  onClosed: () -> Unit,
  onRelaunchFwup: () -> Unit,
): SheetModel {
  val errorMessage = fwupErrorMessage(error, wasInProgress)
  return if (wasInProgress) {
    InProgressFwupUpdateErrorModel(errorMessage, error, deviceInfo, onClosed, onRelaunchFwup)
  } else {
    NotInProgressFwupUpdateErrorModel(errorMessage, error, deviceInfo, onClosed)
  }
}

private fun InProgressFwupUpdateErrorModel(
  errorMessage: FwupErrorMessage,
  error: NfcException,
  deviceInfo: DeviceInfo,
  onClosed: () -> Unit,
  onRelaunchFwup: () -> Unit,
): SheetModel {
  val subline = buildSubline(
    errorMessage = errorMessage,
    deviceInfo = deviceInfo,
    appendResume = true
  )

  return ErrorFormBottomSheetModel(
    onClosed = onClosed,
    title = errorMessage.title,
    subline = subline,
    // On iOS, we encourage the customer to retry the update directly from the error sheet.
    primaryButton =
      when (deviceInfo.devicePlatform) {
        IOS -> ButtonDataModel(text = "Continue", onClick = onRelaunchFwup)
        else -> ButtonDataModel(text = "Got it", onClick = onClosed)
      },
    errorData = fwupUpdateErrorData(error),
    eventTrackerScreenId = FwupEventTrackerScreenId.FWUP_UPDATE_ERROR_SHEET
  )
}

private fun NotInProgressFwupUpdateErrorModel(
  errorMessage: FwupErrorMessage,
  error: NfcException,
  deviceInfo: DeviceInfo,
  onClosed: () -> Unit,
): SheetModel {
  val subline = buildSubline(
    errorMessage = errorMessage,
    deviceInfo = deviceInfo,
    appendResume = false
  )

  return ErrorFormBottomSheetModel(
    onClosed = onClosed,
    title = errorMessage.title,
    subline = subline,
    primaryButton = ButtonDataModel(text = "Got it", onClick = onClosed),
    errorData = fwupUpdateErrorData(error),
    eventTrackerScreenId = FwupEventTrackerScreenId.FWUP_UPDATE_ERROR_SHEET
  )
}

private fun fwupUpdateErrorData(error: NfcException) =
  ErrorData(
    segment = FwupSegment(),
    actionDescription = "Updating firmware",
    cause = error
  )

/**
 * Builds the subline text, optionally appending a resume prompt (for in-progress updates)
 * and airplane mode advice (for problematic iOS devices).
 */
private fun buildSubline(
  errorMessage: FwupErrorMessage,
  deviceInfo: DeviceInfo,
  appendResume: Boolean,
): String {
  val parts = mutableListOf<String>()

  if (appendResume && deviceInfo.devicePlatform == IOS) {
    // For the generic BASE_SUBLINE, preserve prior behavior: inline resume sentence.
    // For error-specific sublines, use a paragraph break for readability.
    if (errorMessage.subline == BASE_SUBLINE) {
      parts.add("${errorMessage.subline} Continue the update to resume where it left off.")
    } else {
      parts.add(errorMessage.subline)
      parts.add("Continue the update to resume where it left off.")
    }
  } else {
    parts.add(errorMessage.subline)
  }

  if (deviceInfo.devicePlatform == IOS && deviceInfo.isAirplaneModeRecommendedForDevice()) {
    parts.add(iOS_AIRPLANE_MODE_MESSAGE)
  }

  return parts.joinToString("\n\n")
}

/**
 * Maps an [NfcException] to a user-facing error title and description
 * specific to firmware update failures.
 *
 * For typed errors (FwupFinishError, Timeout, CanBeRetried), produces error-specific
 * messaging. For generic errors, falls back to in-progress/not-in-progress distinction.
 */
internal fun fwupErrorMessage(
  error: NfcException,
  wasInProgress: Boolean = false,
): FwupErrorMessage =
  when (error) {
    is NfcException.FwupFinishError -> fwupFinishErrorMessage(error.status)
    is NfcException.Timeout -> FwupErrorMessage(
      title = "Update timed out",
      subline = "The connection timed out during the update. Hold your device closer to your phone and try again."
    )
    is NfcException.CanBeRetried -> FwupErrorMessage(
      title = "Connection interrupted",
      subline = "Lost connection to your device during the update. Hold your device steady against the back of your phone and try again."
    )
    else -> FwupErrorMessage(
      title = if (wasInProgress) "Device update not complete" else "Unable to update device",
      subline = BASE_SUBLINE
    )
  }

/**
 * Maps a specific [FwupFinishResponseStatus] to a user-facing error message.
 */
private fun fwupFinishErrorMessage(status: FwupFinishResponseStatus): FwupErrorMessage =
  when (status) {
    FwupFinishResponseStatus.SignatureInvalid -> FwupErrorMessage(
      title = "Signature verification failed",
      subline = "The firmware update could not be verified. Please try again or contact support if the issue persists."
    )
    FwupFinishResponseStatus.VersionInvalid -> FwupErrorMessage(
      title = "Incompatible firmware version",
      subline = "This firmware version is not compatible with your device. Please check for a newer update."
    )
    else -> FwupErrorMessage(
      title = "Update failed",
      subline = "The firmware update could not complete. Please try again."
    )
  }

/**
 * Title and description pair for FWUP error bottom sheets.
 */
internal data class FwupErrorMessage(
  val title: String,
  val subline: String,
)

// Common subline to use that will be further added to for specific device models
const val BASE_SUBLINE =
  "Make sure you hold your device to the back of your phone during the entire update."

@Suppress("TopLevelPropertyNaming")
const val iOS_AIRPLANE_MODE_MESSAGE =
  "If problems persist, turn on Airplane Mode to minimize interruptions."
