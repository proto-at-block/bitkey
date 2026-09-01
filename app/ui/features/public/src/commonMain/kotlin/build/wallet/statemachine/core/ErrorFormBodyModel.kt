package build.wallet.statemachine.core

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.Icon.LargeIconWarningFilled
import build.wallet.statemachine.core.LabelModel.StringModel
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

/**
 * A generic error screen. [errorData] is required so every error a customer sees is
 * logged to Datadog with its segment, action description, and cause.
 */
fun ErrorFormBodyModel(
  title: String,
  subline: String? = null,
  primaryButton: ButtonDataModel,
  onBack: (() -> Unit)? = primaryButton.onClick,
  toolbar: ToolbarModel? = null,
  secondaryButton: ButtonDataModel? = null,
  renderContext: RenderContext = Screen,
  eventTrackerScreenId: EventTrackerScreenId?,
  eventTrackerContext: EventTrackerContext? = null,
  eventTrackerShouldTrack: Boolean = true,
  errorData: ErrorData,
  secondaryButtonIcon: Icon? = null,
): FormBodyModel {
  return ErrorFormBodyModelImpl(
    id = eventTrackerScreenId,
    eventTrackerContext = eventTrackerContext,
    onBack = onBack,
    toolbar = toolbar,
    header =
      FormHeaderModel(
        icon = LargeIconWarningFilled,
        headline = title,
        sublineModel = subline?.let { StringModel(it) },
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

private data class ErrorFormBodyModelImpl(
  override val id: EventTrackerScreenId?,
  override val onBack: (() -> Unit)?,
  override val toolbar: ToolbarModel?,
  override val header: FormHeaderModel?,
  override val primaryButton: ButtonModel?,
  override val secondaryButton: ButtonModel?,
  override val renderContext: RenderContext,
  override val eventTrackerContext: EventTrackerContext?,
  override val eventTrackerShouldTrack: Boolean,
  override val errorData: ErrorData,
) : FormBodyModel(
    id = id,
    onBack = onBack,
    toolbar = toolbar,
    header = header,
    primaryButton = primaryButton,
    secondaryButton = secondaryButton,
    renderContext = renderContext,
    eventTrackerContext = eventTrackerContext,
    eventTrackerShouldTrack = eventTrackerShouldTrack,
    errorData = errorData
  )
