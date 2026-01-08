package build.wallet.ui.app.dev.flags

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsBodyModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.KeyboardType
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun FeatureFlagsScreenPreview() {
  PreviewWalletTheme {
    FeatureFlagsScreen(
      model = FeatureFlagsBodyModel(
        flagsModel =
          ListGroupModel(
            header = null,
            style = ListGroupStyle.DIVIDER,
            items =
              immutableListOf(
                ListItemModel(
                  title = "Flag Title",
                  secondaryText = "Flag Description",
                  trailingAccessory =
                    SwitchAccessory(
                      model =
                        SwitchModel(
                          checked = false,
                          onCheckedChange = {}
                        )
                    )
                )
              )
          ),
        onBack = {},
        onReset = {},
        filterModel = TextFieldModel(
          value = "",
          placeholderText = "Search",
          onValueChange = { _, _ -> },
          keyboardType = KeyboardType.Default,
          focusByDefault = false
        )
      )
    )
  }
}
