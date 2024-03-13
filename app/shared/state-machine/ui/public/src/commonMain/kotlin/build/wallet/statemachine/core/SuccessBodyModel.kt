package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

/**
 * A screen with a static horizontally left-aligned, vertically top-aligned
 * [LargeIconCheckFilled]. If the success should be animated (not static)
 * from a loading animation, use [LoadingSuccessBodyModel].
 *
 * @property id: A unique identifier for this screen that will also be used to track screen
 * analytic events.
 */
fun SuccessBodyModel(
  title: String,
  message: String? = null,
  primaryButtonModel: ButtonDataModel?,
  id: EventTrackerScreenId?,
) = FormBodyModel(
  id = id,
  onBack = null,
  toolbar = null,
  header = FormHeaderModel(
    icon = Icon.LargeIconCheckFilled,
    headline = title,
    subline = message
  ),
  primaryButton = primaryButtonModel?.let {
    ButtonModel(
      text = primaryButtonModel.text,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(primaryButtonModel.onClick)
    )
  }
)
