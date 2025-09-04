package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import dev.zacsweers.redacted.annotations.Redacted

/**
 * A screen with a static horizontally left-aligned, vertically top-aligned
 * [LargeIconCheckFilled]. If the success should be animated (not static)
 * from a loading animation, use [LoadingSuccessBodyModel].
 *
 * @property id: A unique identifier for this screen that will also be used to track screen
 * analytic events.
 */
data class SuccessBodyModel(
  val title: String,
  @Redacted
  val message: String? = null,
  val primaryButtonModel: ButtonDataModel?,
  override val id: EventTrackerScreenId?,
) : FormBodyModel(
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
