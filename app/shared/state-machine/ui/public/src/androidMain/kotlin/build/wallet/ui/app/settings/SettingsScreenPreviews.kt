package build.wallet.ui.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun SettingsScreenPreview() {
  PreviewWalletTheme {
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
                    isDisabled = false,
                    specialTrailingIconModel = IconModel(
                      icon = Icon.SmallIconInformationFilled,
                      iconSize = IconSize.Small,
                      iconTint = IconTint.Warning
                    )
                  ) {},
                  SettingsBodyModel.RowModel(
                    icon = Icon.SmallIconLock,
                    title = "App Security",
                    isDisabled = false,
                    showNewCoachmark = true
                  ) {}
                )
            )
          )
      )
    )
  }
}
