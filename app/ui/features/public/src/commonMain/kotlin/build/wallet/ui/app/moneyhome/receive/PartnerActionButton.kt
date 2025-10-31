package build.wallet.ui.app.moneyhome.receive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.loading.LoadingBadge
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

/**
 * A circular action button specifically designed for the receive screen.
 * Displays an icon (either local or URL-based) with a label underneath.
 *
 * @param iconModel The icon to display (can be URL image for partners or local icon for actions)
 * @param text Label text to display below the icon
 * @param onClick Callback when the button is clicked
 * @param modifier Optional modifier
 * @param enabled Whether the button is enabled
 * @param isLoading Whether to show a loading indicator instead of the icon
 */
@Composable
fun PartnerActionButton(
  iconModel: IconModel,
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isLoading: Boolean = false,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    FilledIconButton(
      modifier = Modifier
        .size(64.dp)
        .alpha(if (enabled) 1f else 0.5f),
      onClick = onClick,
      enabled = enabled && !isLoading,
      shape = CircleShape,
      colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = WalletTheme.colors.foreground10,
        contentColor = WalletTheme.colors.foreground,
        disabledContainerColor = WalletTheme.colors.foreground10.copy(alpha = 0.5f),
        disabledContentColor = WalletTheme.colors.foreground30
      )
    ) {
      if (isLoading) {
        LoadingBadge(
          modifier = Modifier.size(24.dp),
          color = WalletTheme.colors.foreground
        )
      } else {
        IconImage(
          model = iconModel,
          color = when {
            enabled -> Color.Unspecified
            else -> WalletTheme.colors.foreground30
          }
        )
      }
    }

    Spacer(Modifier.height(8.dp))

    Label(
      text = text,
      type = LabelType.Body4Regular,
      treatment = if (enabled) LabelTreatment.Secondary else LabelTreatment.Disabled
    )
  }
}

/**
 * Partner action button with a URL image that fills the entire circular button
 */
@Composable
fun PartnerActionButton(
  logoUrl: String?,
  name: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  fallbackIcon: Icon = Icon.Bitcoin,
  isLoading: Boolean = false,
) {
  PartnerActionButton(
    iconModel = IconModel(
      iconImage = when (logoUrl) {
        null -> IconImage.LocalImage(fallbackIcon)
        else -> IconImage.UrlImage(
          url = logoUrl,
          fallbackIcon = fallbackIcon
        )
      },
      // Avatar size to fill the button completely
      iconSize = IconSize.Avatar
    ),
    text = name,
    onClick = onClick,
    modifier = modifier,
    isLoading = isLoading
  )
}

/**
 * Action button (Share/Copy) with a smaller icon inside the circular button
 */
@Composable
fun ActionButton(
  icon: Icon,
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  iconTint: IconTint? = IconTint.Foreground,
) {
  PartnerActionButton(
    iconModel = IconModel(
      iconImage = IconImage.LocalImage(icon),
      // Smaller icon size for action buttons
      iconSize = IconSize.Small,
      iconTint = iconTint
    ),
    text = text,
    onClick = onClick,
    modifier = modifier
  )
}
