package build.wallet.ui.model.toast

import build.wallet.platform.random.uuid
import build.wallet.ui.model.icon.IconModel

/**
 * View model for a toast that's displayed and automatically dismisses after a short period of time.
 * @property leadingIcon - the icon to be displayed on the left side of the toast
 * @property title - the text of the toast
 * @property id - the unique identifier of the toast
 * @property iconStrokeColor - whether the leading icon should have specific stroke color instead of being transparent
 */
data class ToastModel(
  val leadingIcon: IconModel?,
  val title: String,
  val id: String = uuid(),
  val iconStrokeColor: IconStrokeColor,
) {
  enum class IconStrokeColor {
    Unspecified,
    White,
    Black,
  }
}
