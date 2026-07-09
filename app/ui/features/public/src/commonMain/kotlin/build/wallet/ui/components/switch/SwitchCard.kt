package build.wallet.ui.components.switch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.model.icon.IconSize.Accessory
import build.wallet.ui.model.icon.IconSize.Regular
import build.wallet.ui.model.icon.IconTint.On30
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.switch.SwitchCardModel
import build.wallet.ui.tokens.LabelType

@Composable
fun SwitchCard(
  model: SwitchCardModel,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.Start
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 40.dp)
    ) {
      model.icon?.let { icon ->
        Icon(
          icon = icon,
          size = Regular
        )
        Spacer(modifier = Modifier.height(12.dp))
      }

      Box(
        modifier = Modifier.fillMaxWidth()
      ) {
        Box(
          modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(end = ToggleReservedWidth)
        ) {
          Label(text = model.title, type = LabelType.Body2MonoCaps)
        }

        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
          Switch(
            checked = model.switchModel.checked,
            enabled = model.switchModel.enabled,
            onCheckedChange = model.switchModel.onCheckedChange
          )
        }
      }

      Label(
        modifier = Modifier.padding(end = ToggleReservedWidth),
        text = model.subline,
        type = LabelType.Body3Regular,
        alignment = TextAlign.Start,
        treatment = Secondary
      )
    }

    if (model.actionRows.isNotEmpty()) {
      Divider()
      model.actionRows.forEach { actionRow ->
        ListItem(
          title = actionRow.title,
          titleType = LabelType.Body2Regular,
          trailingAccessory = ListItemAccessory.drillIcon(
            tint = On30,
            iconSize = Accessory
          ),
          secondarySideText = actionRow.sideText,
          onClick = actionRow.onClick
        )
        Divider()
      }
    }
  }
}

private val ToggleReservedWidth = 68.dp
