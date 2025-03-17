package build.wallet.ui.components.switch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.ui.model.switch.SwitchCardModel
import build.wallet.ui.model.switch.SwitchCardModel.ActionRow
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.tooling.PreviewWalletTheme

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
