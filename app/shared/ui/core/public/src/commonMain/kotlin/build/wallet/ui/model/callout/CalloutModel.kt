package build.wallet.ui.model.callout

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.model.Click

/**
 * View model for a callout that can be displayed in the UI.
 * @property title - the title of the callout
 * @property subtitle - the subtitle of the callout
 * @property treatment - the treatment of the callout
 * @property leadingIcon - the icon to be displayed on the left side of the callout
 * @property trailingIcon - the icon to be displayed on the right side of the callout
 * @property onClick - the action to be performed when the trailing icon is clicked
 */
data class CalloutModel(
  val title: String? = null,
  val subtitle: LabelModel? = null,
  val treatment: Treatment = Treatment.Default,
  val leadingIcon: Icon? = null,
  val trailingIcon: Icon? = null,
  val onClick: Click? = null,
  val onTitleClick: Click? = null,
) {
  enum class Treatment {
    Default,
    DefaultCentered,
    Information,
    Success,
    Warning,
    Danger,
  }
}
