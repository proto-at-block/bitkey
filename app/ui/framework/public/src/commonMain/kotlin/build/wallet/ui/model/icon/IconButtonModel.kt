package build.wallet.ui.model.icon

import build.wallet.ui.model.Click

/**
 * Represents a button that will be in the form of an icon
 * @property - iconModel: Model for the Icon displayed in the button
 * @property - onClick: Action taken when button is clicked
 * @property - enabled: Whether the button is clickable (true) or not (false)
 */
data class IconButtonModel(
  val iconModel: IconModel,
  val onClick: Click,
  val enabled: Boolean = true,
)
