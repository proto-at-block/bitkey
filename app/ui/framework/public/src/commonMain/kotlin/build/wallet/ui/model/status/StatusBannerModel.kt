package build.wallet.ui.model.status

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.ui.components.status.StatusBanner
import build.wallet.ui.model.ComposeModel

/**
 * Model for a status banner UI component that is meant to be attached to the top edge of the
 * screen. It shows an informational title and subtitle and performs an action on click.
 *
 * Mainly used to convey availability / functionality status in the app (i.e. when offline).
 * The background will be a warning color and all text will have a warning foreground color.
 * A SmallIconInfo icon will be displayed to the right of the title.
 */
data class StatusBannerModel(
  val title: String,
  val subtitle: String?,
  val style: BannerStyle,
  val onClick: (() -> Unit)?,
) : ComposeModel {
  @Composable
  override fun render(modifier: Modifier) {
    StatusBanner(modifier, model = this)
  }
}

/**
 * A style for the status banner. This is used to determine the background colors
 */
sealed interface BannerStyle {
  /**
   * Warning style is mapped to warning color palette.
   */
  data object Warning : BannerStyle

  /**
   * Destructive style is mapped to destructive color palette.
   */
  data object Destructive : BannerStyle
}
