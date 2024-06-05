package build.wallet.ui.model.toast

import build.wallet.platform.random.uuid
import build.wallet.ui.model.icon.IconModel

/**
 * View model for a toast that's displayed and automatically dismisses after a short period of time.
 * @property leadingIcon - the icon to be displayed on the left side of the toast
 * @property whiteIconStroke - whether the leading icon should have a white stroke instead of being transparent
 * @property title - the text of the toast
 * @property id - the unique identifier of the toast
 */
data class ToastModel(
  val leadingIcon: IconModel?,
  val whiteIconStroke: Boolean = false,
  val title: String,
  val id: String = uuid().random().toString(),
)
