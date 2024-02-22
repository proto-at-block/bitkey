package build.wallet.statemachine.fwup

import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import build.wallet.platform.device.DeviceInfo
import build.wallet.platform.device.DevicePlatform.IOS
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBottomSheetModel
import build.wallet.statemachine.core.SheetModel

/**
 * @param deviceInfoProvider: Provides information about the phone in order to customize messaging.
 * @param wasInProgress: Whether or not the FWUP was in progress before the error occurred.
 */
fun FwupUpdateErrorModel(
  deviceInfo: DeviceInfo,
  wasInProgress: Boolean,
  onClosed: () -> Unit,
  onRelaunchFwup: () -> Unit,
) = when (wasInProgress) {
  true -> InProgressFwupUpdateErrorModel(deviceInfo, onClosed, onRelaunchFwup)
  false -> NotInProgressFwupUpdateErrorModel(deviceInfo, onClosed)
}

private fun InProgressFwupUpdateErrorModel(
  deviceInfo: DeviceInfo,
  onClosed: () -> Unit,
  onRelaunchFwup: () -> Unit,
): SheetModel {
  val iosInProgressSubline = "$BASE_SUBLINE Continue the update to resume where it left off."
  val isAirplaneModeRecommendedForDevice = deviceInfo.isAirplaneModeRecommendedForDevice()

  return ErrorFormBottomSheetModel(
    onClosed = onClosed,
    title = "Device update not complete",
    subline =
      when (deviceInfo.devicePlatform) {
        IOS ->
          if (isAirplaneModeRecommendedForDevice) {
            "$iosInProgressSubline\n\n$iOS_AIRPLANE_MODE_MESSAGE"
          } else {
            iosInProgressSubline
          }
        else -> BASE_SUBLINE
      },
    // On iOS, we encourage the customer to retry the update directly from the error sheet.
    primaryButton =
      when (deviceInfo.devicePlatform) {
        IOS -> ButtonDataModel(text = "Continue", onClick = onRelaunchFwup)
        else -> ButtonDataModel(text = "Got it", onClick = onClosed)
      },
    eventTrackerScreenId = FwupEventTrackerScreenId.FWUP_UPDATE_ERROR_SHEET
  )
}

private fun NotInProgressFwupUpdateErrorModel(
  deviceInfo: DeviceInfo,
  onClosed: () -> Unit,
): SheetModel {
  val isAirplaneModeRecommendedForDevice = deviceInfo.isAirplaneModeRecommendedForDevice()

  return ErrorFormBottomSheetModel(
    onClosed = onClosed,
    title = "Unable to update device",
    subline =
      if (isAirplaneModeRecommendedForDevice) {
        "$BASE_SUBLINE\n\n$iOS_AIRPLANE_MODE_MESSAGE"
      } else {
        BASE_SUBLINE
      },
    primaryButton = ButtonDataModel(text = "Got it", onClick = onClosed),
    eventTrackerScreenId = FwupEventTrackerScreenId.FWUP_UPDATE_ERROR_SHEET
  )
}

// Common subline to use that will be further added to for specific device models
const val BASE_SUBLINE =
  "Make sure you hold your device to the back of your phone during the entire update."

@Suppress("TopLevelPropertyNaming")
const val iOS_AIRPLANE_MODE_MESSAGE =
  "If problems persist, turn on Airplane Mode to minimize interruptions."
