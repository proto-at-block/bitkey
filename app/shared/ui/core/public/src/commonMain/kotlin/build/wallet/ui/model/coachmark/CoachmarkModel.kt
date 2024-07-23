package build.wallet.ui.model.coachmark

import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.button.ButtonModel

/**
 * Model for a popover-style coachmark that is shown to the user.
 * @property identifier The identifier of the coachmark
 * @property title The title of the coachmark
 * @property description The description of the coachmark
 * @property arrowPosition The position of the arrow on the coachmark
 * @property button The button to show on the coachmark (if any)
 * @property image The image to show on the coachmark (if any)
 * @property dismiss The function to call when the coachmark is dismissed
 */
data class CoachmarkModel(
  val identifier: CoachmarkIdentifier,
  val title: String,
  val description: String,
  val arrowPosition: ArrowPosition,
  val button: ButtonModel?,
  val image: Icon?,
  val dismiss: () -> Unit,
) {
  data class ArrowPosition(
    val vertical: Vertical,
    val horizontal: Horizontal,
  ) {
    enum class Vertical {
      Top,
      Bottom,
    }

    enum class Horizontal {
      Leading,
      Centered,
      Trailing,
    }
  }
}
