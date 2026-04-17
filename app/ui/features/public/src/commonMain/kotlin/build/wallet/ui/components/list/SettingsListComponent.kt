package build.wallet.ui.components.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconBackgroundType.Transient
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
fun SettingsListComponent(
  model: FormMainContentModel.SettingsList,
  modifier: Modifier = Modifier,
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  Column(modifier = modifier) {
    // Header
    Label(
      modifier = Modifier.padding(top = 8.dp),
      text = model.header,
      treatment = LabelTreatment.Secondary,
      type = if (isDesignSystemV2Enabled) LabelType.Body3Mono else LabelType.Body3Medium
    )

    // Items
    model.items.forEach { item ->
      ListItem(
        title = item.title,
        contentSpacing = if (isDesignSystemV2Enabled) 12.dp else 8.dp,
        listItemTreatment = item.treatment,
        titleType = if (isDesignSystemV2Enabled) LabelType.Body2MonoCaps else LabelType.Body2Medium,
        titleTreatment = when {
          !item.isEnabled -> LabelTreatment.Disabled
          item.treatment == ListItemTreatment.DESTRUCTIVE -> LabelTreatment.Destructive
          else -> LabelTreatment.Primary
        },
        leadingAccessory = ListItemAccessory.IconAccessory(
          model = IconModel(
            icon = item.icon,
            iconSize = if (isDesignSystemV2Enabled) IconSize.Accessory else Small,
            iconBackgroundType = Transient,
            iconTint = when {
              !item.isEnabled -> IconTint.On10
              item.treatment == ListItemTreatment.DESTRUCTIVE -> IconTint.Destructive
              else -> null
            }
          )
        ),
        trailingAccessory =
          if (isDesignSystemV2Enabled) {
            ListItemAccessory.drillIcon(
              tint = IconTint.On30,
              iconSize = IconSize.Accessory
            ).takeIf { item.isEnabled }
          } else {
            ListItemAccessory.drillIcon(
              tint = IconTint.On30
            ).takeIf { item.isEnabled }
          },
        onClick = if (item.isEnabled) item.onClick else null
      )
      if (isDesignSystemV2Enabled) {
        Divider(
          color = WalletTheme.colors.subtleBackground
        )
      } else {
        Divider()
      }
    }
  }
}
