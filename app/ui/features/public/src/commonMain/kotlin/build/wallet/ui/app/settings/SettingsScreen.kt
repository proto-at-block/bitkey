package build.wallet.ui.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListGroup
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconBackgroundType.Transient
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.theme.WalletTheme
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
    headerContent = {
      model.onSecurityHubCoachmarkClick?.let {
        ListGroup(
          modifier = Modifier.background(color = WalletTheme.colors.secondary),
          model = ListGroupModel(
            style = ListGroupStyle.CARD_ITEM,
            items = immutableListOf(
              ListItemModel(
                title = "Looking for something?",
                secondaryText = "Your Security & Recovery settings now live in the new Security Hub.",
                leadingAccessory = ListItemAccessory.IconAccessory(
                  model = IconModel(
                    icon = Icon.SmallIconShield,
                    iconSize = Small,
                    iconTint = IconTint.White,
                    iconBackgroundType = IconBackgroundType.Circle(
                      color = IconBackgroundType.Circle.CircleColor.BitkeyPrimary,
                      circleSize = IconSize.Large
                    )
                  )
                ),
                trailingAccessory = ListItemAccessory.IconAccessory(
                  model = IconModel(
                    icon = Icon.SmallIconArrowRight,
                    iconSize = Small,
                    iconBackgroundType = Transient,
                    iconTint = IconTint.On30
                  ),
                  onClick = {
                    model.onSecurityHubCoachmarkClick.invoke()
                  }
                ),
                onClick = {
                  model.onSecurityHubCoachmarkClick.invoke()
                }
              )
            )
          )
        )
      }
    },
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
                iconTint = if (rowModel.isDisabled) IconTint.On10 else null
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
