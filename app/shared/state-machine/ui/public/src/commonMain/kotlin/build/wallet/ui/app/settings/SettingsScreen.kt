package build.wallet.ui.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

@Composable
fun SettingsScreen(
  modifier: Modifier = Modifier,
  model: SettingsBodyModel,
) {
  FormScreen(
    modifier = modifier,
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
        onClick = rowModel.onClick,
        showNewCoachmark = rowModel.showNewCoachmark
      )
      Divider()
    }
  }
}
