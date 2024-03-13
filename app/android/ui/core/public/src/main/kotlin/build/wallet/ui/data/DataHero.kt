package build.wallet.ui.data

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.DataHero
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.XLarge
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
internal fun DataHero(
  modifier: Modifier = Modifier,
  model: DataHero,
) {
  Column(
    modifier = modifier.padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    model.image?.let {
      IconImage(
        model = it
      )
    }
    Spacer(modifier = Modifier.height(16.dp))
    model.title?.let { title ->
      Label(
        text = title,
        type = LabelType.Title3
      )
    }
    model.subtitle?.let { subtitle ->
      Spacer(modifier = Modifier.height(4.dp))
      Label(
        text = subtitle,
        type = LabelType.Body3Bold,
        treatment = Secondary
      )
    }
    model.button?.let {
      Spacer(modifier = Modifier.height(16.dp))
      Button(model = it)
    }
  }
}

@Preview
@Composable
fun PreviewDataHeroWithoutButton() {
  PreviewWalletTheme {
    DataHero(
      model =
        DataHero(
          image =
            IconModel(
              iconImage =
                LocalImage(
                  icon = Icon.BitkeyDevice3D
                ),
              iconSize = XLarge
            ),
          title = "Device is up to date",
          subtitle = "16.1.14",
          button = null
        )
    )
  }
}

@Preview
@Composable
fun PreviewDataHeroWithButton() {
  PreviewWalletTheme {
    DataHero(
      model =
        DataHero(
          image =
            IconModel(
              iconImage =
                LocalImage(
                  icon = Icon.BitkeyDevice3D
                ),
              iconSize = XLarge
            ),
          title = "Update available",
          subtitle = "16.1.14",
          button =
            ButtonModel(
              text = "Update",
              isEnabled = false,
              isLoading = false,
              leadingIcon = null,
              treatment = Primary,
              size = Footer,
              onClick = StandardClick {}
            )
        )
    )
  }
}
