package build.wallet.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.XLarge
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun PreviewDataHeroWithoutButton() {
  PreviewWalletTheme {
    DataHero(
      model =
        DataList.DataHero(
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
        DataList.DataHero(
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
