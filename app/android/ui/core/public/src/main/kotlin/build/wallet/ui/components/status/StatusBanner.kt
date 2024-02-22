package build.wallet.ui.components.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.icon.IconStyle
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.status.StatusBannerModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

// All status banners currently use a warning background color
val StatusBannerModel.backgroundColor: Color
  @Composable
  get() = WalletTheme.colors.warning

@Composable
fun StatusBanner(model: StatusBannerModel) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(model.backgroundColor)
        .thenIf(model.onClick != null) {
          model.onClick?.let {
            Modifier.clickable(
              indication = null,
              interactionSource = remember { MutableInteractionSource() },
              onClick = it
            )
          } ?: Modifier
        }
        .padding(horizontal = 20.dp)
        .padding(top = 12.dp, bottom = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      StatusBannerLabel(text = model.title, type = LabelType.Body3Medium)
      model.onClick?.let {
        IconImage(
          modifier = Modifier.padding(start = 4.dp),
          model =
            IconModel(
              iconImage = IconImage.LocalImage(Icon.SmallIconInformationFilled),
              iconSize = IconSize.XSmall
            ),
          style =
            IconStyle(
              color = WalletTheme.colors.warningForeground
            )
        )
      }
    }

    model.subtitle?.let {
      StatusBannerLabel(text = it, type = LabelType.Body4Regular)
    }
  }
}

@Composable
private fun StatusBannerLabel(
  text: String,
  type: LabelType,
) {
  Label(
    text = text,
    type = type,
    treatment = LabelTreatment.Unspecified,
    color = WalletTheme.colors.warningForeground,
    alignment = TextAlign.Center
  )
}
