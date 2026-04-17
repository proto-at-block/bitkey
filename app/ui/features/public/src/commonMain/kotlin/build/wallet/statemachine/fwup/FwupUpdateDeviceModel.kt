package build.wallet.statemachine.fwup

import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import bitkey.account.HardwareType
import build.wallet.nfc.NfcException
import build.wallet.platform.device.DeviceInfo
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference

fun FwupUpdateDeviceModel(
  devicePlatform: DevicePlatform,
  hardwareType: HardwareType,
  onLaunchFwup: () -> Unit,
  onClose: () -> Unit,
  onReleaseNotes: () -> Unit,
  bottomSheetModel: FwupUpdateDeviceBottomSheet?,
) = ScreenModel(
  body =
    FwupInstructionsBodyModel(
      onClose = onClose,
      headerModel =
        FormHeaderModel(
          headline = "Update your device",
          sublineModel = LabelModel.LinkSubstringModel.from(
            substringToOnClick = mapOf(
              Pair(
                first = "release notes",
                second = {
                  onReleaseNotes.invoke()
                }
              )
            ),
            string = "Press the button below and hold your unlocked device to the back of your phone until the update has completed. To learn more about this firmware update, see the release notes.",
            underline = true,
            bold = true,
            color = LabelModel.Color.FOREGROUND
          ),
          alignment = CENTER
      ),
      buttonText = "Update Bitkey",
      onButtonClick = onLaunchFwup,
      hardwareType = hardwareType,
      eventTrackerScreenId = FwupEventTrackerScreenId.FWUP_UPDATE_INSTRUCTIONS
    ),
  presentationStyle = ScreenPresentationStyle.ModalFullScreen,
  bottomSheetModel =
    when (bottomSheetModel) {
      null -> null
      is FwupUpdateDeviceBottomSheet.UnauthenticatedErrorModel ->
        FwupUnauthenticatedErrorModel(bottomSheetModel.onClosed)
      is FwupUpdateDeviceBottomSheet.PreviousMcuUpdateNotAppliedModel ->
        FwupPreviousMcuUpdateNotAppliedModel(
          onClosed = bottomSheetModel.onClosed,
          onRelaunchFwup = bottomSheetModel.onRelaunchFwup
        )
      is FwupUpdateDeviceBottomSheet.UpdateErrorModel ->
        FwupUpdateErrorModel(
          error = bottomSheetModel.error,
          deviceInfo = bottomSheetModel.deviceInfo,
          wasInProgress = bottomSheetModel.wasInProgress,
          onClosed = bottomSheetModel.onClosed,
          onRelaunchFwup = bottomSheetModel.onRelaunchFwup
        )
    },
  themePreference =
    if (hardwareType == bitkey.account.HardwareType.W3) {
      fwupThemePreference(devicePlatform)
    } else {
      ThemePreference.Manual(Theme.DARK)
    }
)

sealed interface FwupUpdateDeviceBottomSheet {
  data class UnauthenticatedErrorModel(
    val onClosed: () -> Unit,
  ) : FwupUpdateDeviceBottomSheet

  data class UpdateErrorModel(
    val error: NfcException,
    val onClosed: () -> Unit,
    val onRelaunchFwup: () -> Unit,
    val deviceInfo: DeviceInfo,
    val wasInProgress: Boolean,
  ) : FwupUpdateDeviceBottomSheet

  data class PreviousMcuUpdateNotAppliedModel(
    val onClosed: () -> Unit,
    val onRelaunchFwup: () -> Unit,
  ) : FwupUpdateDeviceBottomSheet
}
