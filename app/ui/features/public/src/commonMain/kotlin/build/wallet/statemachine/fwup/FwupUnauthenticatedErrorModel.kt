package build.wallet.statemachine.fwup

import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBottomSheetModel
import build.wallet.statemachine.core.SheetModel

fun FwupUnauthenticatedErrorModel(onClosed: () -> Unit): SheetModel {
  return ErrorFormBottomSheetModel(
    onClosed = onClosed,
    title = "Device Locked",
    subline = "Unlock your device with an enrolled fingerprint and try again.",
    primaryButton =
      ButtonDataModel(
        text = "Got it",
        onClick = onClosed
      ),
    errorData = ErrorData(
      segment = FwupSegment(),
      actionDescription = "Authenticating firmware update",
      cause = IllegalStateException("Firmware update attempted while device was locked")
    ),
    eventTrackerScreenId = FwupEventTrackerScreenId.FWUP_UNAUTHENTICATED_ERROR_SHEET
  )
}
