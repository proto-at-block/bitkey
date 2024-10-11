package build.wallet.ui.components.switch

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
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.model.icon.IconTint.On30
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.switch.SwitchCardModel
import build.wallet.ui.model.switch.SwitchCardModel.ActionRow
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SwitchCard(
  model: SwitchCardModel,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Column(
      modifier = Modifier.padding(vertical = 40.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Switch(
        checked = model.switchModel.checked,
        onCheckedChange = model.switchModel.onCheckedChange
      )
      Spacer(modifier = Modifier.height(24.dp))
      Label(text = model.title, type = LabelType.Title2)
      Spacer(modifier = Modifier.height(8.dp))
      Label(
        text = model.subline,
        type = LabelType.Body3Regular,
        alignment = TextAlign.Center,
        treatment = Secondary
      )
    }
    model.actionRows.forEach { actionRow ->
      Divider()
      ListItem(
        title = actionRow.title,
        titleType = LabelType.Body2Regular,
        trailingAccessory = ListItemAccessory.drillIcon(tint = On30),
        secondarySideText = actionRow.sideText,
        onClick = actionRow.onClick
      )
    }
  }
}

@Preview
@Composable
fun SwitchCardPreview() {
  PreviewWalletTheme {
    Column {
      SwitchCard(
        modifier = Modifier.fillMaxWidth(),
        model =
          SwitchCardModel(
            title = "Card Title",
            subline = "Card Description",
            switchModel =
              SwitchModel(
                checked = true,
                onCheckedChange = {}
              ),
            actionRows =
              immutableListOf(
                ActionRow(
                  title = "Daily limit",
                  sideText = "$25.00",
                  onClick = {}
                ),
                ActionRow(
                  title = "Connected to:",
                  sideText = "ssl://bitkey.mempool.space:50002",
                  onClick = {}
                )
              )
          )
      )
      Spacer(Modifier.height(5.dp))
      SwitchCard(
        modifier = Modifier.fillMaxWidth(),
        model =
          SwitchCardModel(
            title = "Card Title",
            subline = "Card Description",
            switchModel =
              SwitchModel(
                checked = false,
                onCheckedChange = {}
              ),
            actionRows = emptyImmutableList()
          )
      )
    }
  }
}
