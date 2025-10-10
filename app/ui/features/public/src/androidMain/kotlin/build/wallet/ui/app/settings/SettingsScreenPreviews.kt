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

@Preview
@Composable
fun SettingsScreenWithSecurityHubCoachmarkPreview() {
  PreviewWalletTheme {
    SettingsScreen {}
  }
}

@Composable
fun SettingsScreen(securityHubClickHandler: (() -> Unit)? = null) {
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
                  icon = Icon.SmallIconPhone,
                  title = "Mobile Pay",
                  isDisabled = true
                ) {},
                SettingsBodyModel.RowModel(
                  icon = Icon.SmallIconBitkey,
                  title = "Lost or Stolen Device",
                  isDisabled = true
                ) {},
                SettingsBodyModel.RowModel(
                  icon = Icon.SmallIconQuestion,
                  title = "Help Center",
                  isDisabled = false
                ) {},
                SettingsBodyModel.RowModel(
                  icon = Icon.SmallIconCloud,
                  title = "Cloud Backup",
                  isDisabled = false
                ) {},
                SettingsBodyModel.RowModel(
                  icon = Icon.SmallIconLock,
                  title = "App Security",
                  isDisabled = false,
                  coachmarkLabelModel = CoachmarkLabelModel.New
                ) {},
                SettingsBodyModel.RowModel(
                  icon = Icon.SmallIconWallet,
                  title = "Enhanced Wallet Privacy",
                  isDisabled = false,
                  coachmarkLabelModel = CoachmarkLabelModel.Upgrade
                ) {}
              )
          )
        ),
      toolbarModel = ToolbarModel(
        leadingAccessory = BackAccessory(onClick = {}),
        middleAccessory = ToolbarMiddleAccessoryModel(title = "Settings")
      ),
      onSecurityHubCoachmarkClick = securityHubClickHandler
    )
  )
}
