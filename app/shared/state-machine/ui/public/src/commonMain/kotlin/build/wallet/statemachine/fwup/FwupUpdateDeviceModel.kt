package build.wallet.statemachine.fwup

import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import build.wallet.platform.device.DeviceInfo
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel

fun FwupUpdateDeviceModel(
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
            bold = true
          ),
          alignment = CENTER
        ),
      buttonText = "Update Bitkey",
      onButtonClick = onLaunchFwup,
      eventTrackerScreenId = FwupEventTrackerScreenId.FWUP_UPDATE_INSTRUCTIONS
    ),
  presentationStyle = ScreenPresentationStyle.ModalFullScreen,
  bottomSheetModel =
    when (bottomSheetModel) {
      null -> null
      is FwupUpdateDeviceBottomSheet.UnauthenticatedErrorModel ->
        FwupUnauthenticatedErrorModel(bottomSheetModel.onClosed)
      is FwupUpdateDeviceBottomSheet.UpdateErrorModel ->
        FwupUpdateErrorModel(
          deviceInfo = bottomSheetModel.deviceInfo,
          wasInProgress = bottomSheetModel.wasInProgress,
          onClosed = bottomSheetModel.onClosed,
          onRelaunchFwup = bottomSheetModel.onRelaunchFwup
        )
    }
)

sealed interface FwupUpdateDeviceBottomSheet {
  data class UnauthenticatedErrorModel(
    val onClosed: () -> Unit,
  ) : FwupUpdateDeviceBottomSheet

  data class UpdateErrorModel(
    val onClosed: () -> Unit,
    val onRelaunchFwup: () -> Unit,
    val deviceInfo: DeviceInfo,
    val wasInProgress: Boolean,
  ) : FwupUpdateDeviceBottomSheet
}
