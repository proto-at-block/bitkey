package build.wallet.ui.model.list

import build.wallet.ui.model.coachmark.CoachmarkLabelTreatment

/**
 * Model for a badge-style coachmark that displays text on list items and popover coachmarks.
 */
data class CoachmarkLabelModel(
  val text: String,
  val treatment: CoachmarkLabelTreatment = CoachmarkLabelTreatment.Light,
) {
  companion object {
    val New = CoachmarkLabelModel(text = "New")
    val Upgrade = CoachmarkLabelModel(text = "Upgrade", treatment = CoachmarkLabelTreatment.Dark)
  }
}
