package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.form.RenderContext.Sheet

@Deprecated("Specify [errorData] argument")
fun ErrorFormBottomSheetModel(
  title: String,
  subline: String? = null,
  primaryButton: ButtonDataModel,
  secondaryButton: ButtonDataModel? = null,
  eventTrackerScreenId: EventTrackerScreenId?,
  onClosed: () -> Unit,
) = ErrorFormBottomSheetModelWithOptionalErrorData(
  title = title,
  subline = subline,
  primaryButton = primaryButton,
  secondaryButton = secondaryButton,
  eventTrackerScreenId = eventTrackerScreenId,
  errorData = null,
  onClosed = onClosed
)

fun ErrorFormBottomSheetModel(
  title: String,
  subline: String? = null,
  primaryButton: ButtonDataModel,
  secondaryButton: ButtonDataModel? = null,
  eventTrackerScreenId: EventTrackerScreenId?,
  errorData: ErrorData,
  onClosed: () -> Unit,
) = ErrorFormBottomSheetModelWithOptionalErrorData(
  title = title,
  subline = subline,
  primaryButton = primaryButton,
  secondaryButton = secondaryButton,
  eventTrackerScreenId = eventTrackerScreenId,
  errorData = errorData,
  onClosed = onClosed
)

private fun ErrorFormBottomSheetModelWithOptionalErrorData(
  title: String,
  subline: String? = null,
  primaryButton: ButtonDataModel,
  secondaryButton: ButtonDataModel? = null,
  eventTrackerScreenId: EventTrackerScreenId?,
  errorData: ErrorData?,
  onClosed: () -> Unit,
) = SheetModel(
  onClosed = onClosed,
  body =
    ErrorFormBodyModelWithOptionalErrorData(
      title = title,
      subline = subline,
      primaryButton = primaryButton,
      secondaryButton = secondaryButton,
      eventTrackerScreenId = eventTrackerScreenId,
      renderContext = Sheet,
      errorData = errorData
    )
)
