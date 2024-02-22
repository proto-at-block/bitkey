package build.wallet.statemachine.fwup

import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBottomSheetModel
import build.wallet.statemachine.core.SheetModel

fun FwupUnauthenticatedErrorModel(onClosed: () -> Unit): SheetModel {
  return ErrorFormBottomSheetModel(
    onClosed = onClosed,
    title = "Device Locked",
    subline = "Unlock your device with the fingerprint you enrolled during setup and try again.",
    primaryButton =
      ButtonDataModel(
        text = "Got it",
        onClick = onClosed
      ),
    eventTrackerScreenId = FwupEventTrackerScreenId.FWUP_UNAUTHENTICATED_ERROR_SHEET
  )
}
