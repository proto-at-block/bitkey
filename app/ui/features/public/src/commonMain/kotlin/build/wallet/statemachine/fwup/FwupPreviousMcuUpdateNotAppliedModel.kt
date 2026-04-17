package build.wallet.statemachine.fwup

import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBottomSheetModel
import build.wallet.statemachine.core.SheetModel

/**
 * Bottom sheet shown when the previous MCU's firmware update was not applied on the device.
 * Prompts the user to restart the update from the beginning.
 */
fun FwupPreviousMcuUpdateNotAppliedModel(
  onClosed: () -> Unit,
  onRelaunchFwup: () -> Unit,
): SheetModel =
  ErrorFormBottomSheetModel(
    onClosed = onClosed,
    title = "Previous update not applied",
    subline = "The previous update was not applied on your device. " +
      "Please restart the update to try again.",
    primaryButton = ButtonDataModel(text = "Restart update", onClick = onRelaunchFwup),
    eventTrackerScreenId = FwupEventTrackerScreenId.FWUP_PREVIOUS_MCU_UPDATE_NOT_APPLIED_SHEET
  )
