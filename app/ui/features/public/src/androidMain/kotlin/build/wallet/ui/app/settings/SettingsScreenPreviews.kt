package build.wallet.ui.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.ui.model.list.CoachmarkLabelModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun SettingsScreenPreview() {
  PreviewWalletTheme {
    SettingsScreen()
  }
}

@Composable
fun SettingsScreen(allItemsEnabled: Boolean = false) {
  SettingsScreen(
    model = SettingsBodyModel(
      onBack = {},
      sectionModels =
        immutableListOf(
          SettingsBodyModel.SectionModel(
            sectionHeaderTitle = "General",
            rowModels =
              immutableListOf(
                SettingsBodyModel.RowModel(
                  icon = Icon.Phone,
                  title = "Mobile Pay",
                  isDisabled = !allItemsEnabled
                ) {},
                SettingsBodyModel.RowModel(
                  icon = Icon.Bitkey,
                  title = "Lost or Stolen Device",
                  isDisabled = !allItemsEnabled
                ) {},
                SettingsBodyModel.RowModel(
                  icon = Icon.Question,
                  title = "Help Center",
                  isDisabled = false
                ) {},
                SettingsBodyModel.RowModel(
                  icon = Icon.Cloud,
                  title = "Cloud Backup",
                  isDisabled = false
                ) {},
                SettingsBodyModel.RowModel(
                  icon = Icon.Lock,
                  title = "App Security",
                  isDisabled = false,
                  coachmarkLabelModel = CoachmarkLabelModel.New
                ) {},
                SettingsBodyModel.RowModel(
                  icon = Icon.Wallet,
                  title = "Private Wallet Update",
                  isDisabled = false,
                  coachmarkLabelModel = CoachmarkLabelModel.New
                ) {}
              )
          )
        ),
      toolbarModel = ToolbarModel(
        leadingAccessory = BackAccessory(onClick = {}),
        middleAccessory = ToolbarMiddleAccessoryModel(title = "Settings")
      )
    )
  )
}

@Preview
@Composable
fun SettingsScreenAllItemsEnabledDesignSystemPreview() {
  PreviewWalletTheme {
    SettingsScreen(allItemsEnabled = true)
  }
}
