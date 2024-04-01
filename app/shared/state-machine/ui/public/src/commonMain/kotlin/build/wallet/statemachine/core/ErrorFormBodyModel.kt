package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.context.EventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.Icon.LargeIconWarningFilled
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.toolbar.ToolbarModel

fun ErrorFormBodyModel(
  title: String,
  subline: String? = null,
  primaryButton: ButtonDataModel,
  onBack: (() -> Unit)? = primaryButton.onClick,
  toolbar: ToolbarModel? = null,
  secondaryButton: ButtonDataModel? = null,
  renderContext: RenderContext = Screen,
  eventTrackerScreenId: EventTrackerScreenId?,
  eventTrackerScreenIdContext: EventTrackerScreenIdContext? = null,
  eventTrackerShouldTrack: Boolean = true,
  onLoaded: () -> Unit = {},
  errorData: ErrorData,
  secondaryButtonIcon: Icon? = null,
) = ErrorFormBodyModelWithOptionalErrorData(
  title = title,
  subline = subline,
  primaryButton = primaryButton,
  onBack = onBack,
  toolbar = toolbar,
  secondaryButton = secondaryButton,
  renderContext = renderContext,
  eventTrackerScreenId = eventTrackerScreenId,
  eventTrackerScreenIdContext = eventTrackerScreenIdContext,
  eventTrackerShouldTrack = eventTrackerShouldTrack,
  onLoaded = onLoaded,
  errorData = errorData,
  secondaryButtonIcon = secondaryButtonIcon
)

@Deprecated("Specify [errorData] argument")
fun ErrorFormBodyModel(
  title: String,
  subline: String? = null,
  primaryButton: ButtonDataModel,
  onBack: (() -> Unit)? = primaryButton.onClick,
  toolbar: ToolbarModel? = null,
  secondaryButton: ButtonDataModel? = null,
  renderContext: RenderContext = Screen,
  eventTrackerScreenId: EventTrackerScreenId?,
  eventTrackerScreenIdContext: EventTrackerScreenIdContext? = null,
  eventTrackerShouldTrack: Boolean = true,
  onLoaded: () -> Unit = {},
  secondaryButtonIcon: Icon? = null,
) = ErrorFormBodyModelWithOptionalErrorData(
  title = title,
  subline = subline,
  primaryButton = primaryButton,
  onBack = onBack,
  toolbar = toolbar,
  secondaryButton = secondaryButton,
  renderContext = renderContext,
  eventTrackerScreenId = eventTrackerScreenId,
  eventTrackerScreenIdContext = eventTrackerScreenIdContext,
  eventTrackerShouldTrack = eventTrackerShouldTrack,
  onLoaded = onLoaded,
  errorData = null,
  secondaryButtonIcon = secondaryButtonIcon
)

@Deprecated("Specify [errorData] argument")
fun ErrorFormBodyModelWithOptionalErrorData(
  title: String,
  subline: String? = null,
  primaryButton: ButtonDataModel,
  onBack: (() -> Unit)? = primaryButton.onClick,
  toolbar: ToolbarModel? = null,
  secondaryButton: ButtonDataModel? = null,
  renderContext: RenderContext = Screen,
  eventTrackerScreenId: EventTrackerScreenId?,
  eventTrackerScreenIdContext: EventTrackerScreenIdContext? = null,
  eventTrackerShouldTrack: Boolean = true,
  onLoaded: () -> Unit = {},
  errorData: ErrorData?,
  secondaryButtonIcon: Icon? = null,
): FormBodyModel {
  return FormBodyModel(
    onLoaded = { onLoaded() },
    id = eventTrackerScreenId,
    eventTrackerScreenIdContext = eventTrackerScreenIdContext,
    onBack = onBack,
    toolbar = toolbar,
    header =
      FormHeaderModel(
        icon = LargeIconWarningFilled,
        headline = title,
        subline = subline,
        alignment =
          when (renderContext) {
            Sheet -> CENTER
            Screen -> LEADING
          }
      ),
    primaryButton =
      ButtonModel(
        text = primaryButton.text,
        size = Footer,
        onClick = StandardClick(primaryButton.onClick)
      ),
    renderContext = renderContext,
    secondaryButton =
      secondaryButton?.let { secondary ->
        ButtonModel(
          text = secondary.text,
          treatment = Secondary,
          size = Footer,
          onClick = StandardClick(secondary.onClick),
          leadingIcon = secondaryButtonIcon
        )
      },
    eventTrackerShouldTrack = eventTrackerShouldTrack,
    errorData = errorData
  )
}
