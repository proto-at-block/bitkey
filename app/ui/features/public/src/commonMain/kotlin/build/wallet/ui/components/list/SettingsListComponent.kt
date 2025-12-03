package build.wallet.ui.components.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.LabelTreatment.Destructive
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.tokens.LabelType

@Composable
fun SettingsListComponent(
  model: FormMainContentModel.SettingsList,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    // Header
    Label(
      modifier = Modifier.padding(top = 8.dp),
      text = model.header,
      treatment = LabelTreatment.Secondary,
      type = LabelType.Body3Medium
    )

    // Items
    model.items.forEach { item ->
      ListItem(
        title = item.title,
        listItemTreatment = item.treatment,
        titleTreatment = when {
          !item.isEnabled -> LabelTreatment.Disabled
          item.treatment == ListItemTreatment.DESTRUCTIVE -> LabelTreatment.Destructive
          else -> LabelTreatment.Primary
        },
        leadingAccessory = ListItemAccessory.IconAccessory(
          model = IconModel(
            icon = item.icon,
            iconSize = IconSize.Small,
            iconBackgroundType = IconBackgroundType.Transient,
            iconTint = when {
              !item.isEnabled -> IconTint.On10
              item.treatment == ListItemTreatment.DESTRUCTIVE -> IconTint.Destructive
              else -> null
            }
          )
        ),
        trailingAccessory = if (item.isEnabled) ListItemAccessory.drillIcon(tint = IconTint.On30) else null,
        onClick = if (item.isEnabled) item.onClick else null
      )
      Divider()
    }
  }
}
