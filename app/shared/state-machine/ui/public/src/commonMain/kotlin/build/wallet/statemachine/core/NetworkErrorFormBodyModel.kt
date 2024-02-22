package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.RenderContext

/**
 * Convenience method for common error messaging when the error is for a network request.
 */
fun NetworkErrorFormBodyModel(
  title: String,
  isConnectivityError: Boolean,
  onBack: () -> Unit,
  eventTrackerScreenId: EventTrackerScreenId?,
  renderContext: RenderContext = RenderContext.Screen,
) = ErrorFormBodyModel(
  title = title,
  subline =
    when {
      isConnectivityError -> "Make sure you are connected to the internet and try again."
      else -> "We are looking into this. Please try again later."
    },
  primaryButton =
    ButtonDataModel(
      text = "Back",
      onClick = onBack
    ),
  eventTrackerScreenId = eventTrackerScreenId,
  renderContext = renderContext
)

/**
 * Convenience method for common error messaging when the error is for a network request.
 */
fun NetworkErrorFormBodyModel(
  title: String,
  isConnectivityError: Boolean,
  onRetry: (() -> Unit)?,
  onBack: () -> Unit,
  eventTrackerScreenId: EventTrackerScreenId?,
  renderContext: RenderContext = RenderContext.Screen,
): FormBodyModel {
  val backButtonModel =
    ButtonDataModel(
      text = "Back",
      onClick = onBack
    )

  return ErrorFormBodyModel(
    onBack = onBack,
    title = title,
    subline =
      when {
        isConnectivityError -> "Make sure you are connected to the internet and try again."
        else -> "We are looking into this. Please try again later."
      },
    // Show "Retry" as the primary button if an [onRetry] was provided. Otherwise, use the
    // back button as the primary button.
    primaryButton =
      onRetry?.let {
        ButtonDataModel(
          text = "Retry",
          onClick = it
        )
      } ?: backButtonModel,
    // Show a back button as a secondary button, only if the primary is a retry button
    secondaryButton = onRetry?.let { backButtonModel },
    eventTrackerScreenId = eventTrackerScreenId,
    renderContext = renderContext
  )
}
