package build.wallet.ui.model.status

import build.wallet.ui.model.Model

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
  val onClick: (() -> Unit)?,
) : Model()
