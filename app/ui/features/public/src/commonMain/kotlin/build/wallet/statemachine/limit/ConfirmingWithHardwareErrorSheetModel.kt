package build.wallet.statemachine.limit

import build.wallet.analytics.events.screen.id.MobilePayEventTrackerScreenId.MOBILE_PAY_LIMIT_UPDATE_HW_APPROVAL_ERROR_SHEET
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.RenderContext

fun ConfirmingWithHardwareErrorSheetModel(
  isConnectivityError: Boolean,
  onClosed: () -> Unit,
): SheetModel {
  return SheetModel(
    onClosed = onClosed,
    body =
      ErrorFormBodyModel(
        title = "We were unable to set your spending limit",
        subline =
          when {
            isConnectivityError -> "Make sure you are connected to the internet and try again."
            else -> "We are looking into this. Please try again later."
          },
        primaryButton =
          ButtonDataModel(
            text = "Back",
            onClick = onClosed
          ),
        renderContext = RenderContext.Sheet,
        eventTrackerScreenId = MOBILE_PAY_LIMIT_UPDATE_HW_APPROVAL_ERROR_SHEET
      )
  )
}
