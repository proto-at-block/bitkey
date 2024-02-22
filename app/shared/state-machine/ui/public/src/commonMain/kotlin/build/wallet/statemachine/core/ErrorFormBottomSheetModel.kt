package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.form.RenderContext.Sheet

fun ErrorFormBottomSheetModel(
  title: String,
  subline: String? = null,
  primaryButton: ButtonDataModel,
  secondaryButton: ButtonDataModel? = null,
  eventTrackerScreenId: EventTrackerScreenId?,
  onClosed: () -> Unit,
) = SheetModel(
  onClosed = onClosed,
  body =
    ErrorFormBodyModel(
      title = title,
      subline = subline,
      primaryButton = primaryButton,
      secondaryButton = secondaryButton,
      eventTrackerScreenId = eventTrackerScreenId,
      renderContext = Sheet
    )
)
