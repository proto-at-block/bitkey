package build.wallet.ui.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.icon.IconBackgroundType.Transient
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun SettingsScreen(model: SettingsBodyModel) {
  FormScreen(
    onBack = model.onBack,
    toolbarContent = {
      Toolbar(model = model.toolbarModel)
    },
    headerContent = { },
    mainContent = {
      Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        for (sectionModel in model.sectionModels) {
          SettingsSection(sectionModel)
        }
      }
    }
  )
}

@Composable
private fun SettingsSection(model: SettingsBodyModel.SectionModel) {
  Column {
    // Section title
    Label(
      modifier = Modifier.padding(top = 8.dp),
      text = model.sectionHeaderTitle,
      treatment = LabelTreatment.Secondary,
      type = LabelType.Title3
    )

    // Section rows
    for (rowModel in model.rowModels) {
      ListItem(
        title = rowModel.title,
        titleTreatment = if (rowModel.isDisabled) LabelTreatment.Disabled else LabelTreatment.Primary,
        leadingAccessory =
          ListItemAccessory.IconAccessory(
            model =
              IconModel(
                icon = rowModel.icon,
                iconSize = Small,
                iconBackgroundType = Transient,
                iconTint = if (rowModel.isDisabled) IconTint.On30 else null
              )
          ),
        trailingAccessory = ListItemAccessory.drillIcon().takeIf { !rowModel.isDisabled },
        specialTrailingAccessory = rowModel.specialTrailingIconModel?.let { model ->
          ListItemAccessory.IconAccessory(model = model)
        },
        onClick = rowModel.onClick
      )
      Divider()
    }
  }
}

@Preview
@Composable
internal fun SettingsScreenPreview() {
  PreviewWalletTheme {
    SettingsScreen(
      SettingsBodyModel(
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
                      iconSize = Small,
                      iconTint = IconTint.Warning
                    )
                  ) {}
                )
            )
          )
      )
    )
  }
}
