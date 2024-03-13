package build.wallet.ui.app.dev.lightning

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.dev.lightning.ChannelOpenRowModel
import build.wallet.statemachine.dev.lightning.ChannelsModel
import build.wallet.statemachine.dev.lightning.ConnectAndOpenChannelBodyModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.forms.TextField
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun ConnectAndOpenChannelScreen(model: ConnectAndOpenChannelBodyModel) {
  val fundingAmountValue = remember { mutableStateOf(TextFieldValue(text = model.fundingAmount)) }
  val peerNodeIdValue = remember { mutableStateOf(TextFieldValue(text = model.peerNodeId)) }
  val peerAddressValue = remember { mutableStateOf(TextFieldValue(text = model.peerAddress)) }

  BackHandler(onBack = model.onBack)
  LazyColumn(
    modifier =
      Modifier
        .padding(horizontal = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    item {
      Toolbar(
        model =
          ToolbarModel(
            leadingAccessory = BackAccessory(onClick = model.onBack),
            middleAccessory = ToolbarMiddleAccessoryModel(title = "Open Channel")
          )
      )

      Spacer(Modifier.height(24.dp))
      Label(text = model.onchainBalanceLabelString, type = LabelType.Body2Regular)
      Spacer(Modifier.height(24.dp))

      Divider()

      Spacer(Modifier.height(24.dp))
      Label(text = "Enter peer node ID", type = LabelType.Body2Regular)
      Spacer(Modifier.height(24.dp))
      TextField(
        modifier = Modifier.fillMaxWidth(),
        placeholderText = "NODE_ID",
        value = peerNodeIdValue.value,
        onValueChange = { newValue ->
          peerNodeIdValue.value = newValue
          model.onPeerNodeIdChanged(newValue.text)
        }
      )
      Spacer(Modifier.height(24.dp))
      Label(text = "Enter peer address", type = LabelType.Body2Regular)
      Spacer(Modifier.height(24.dp))
      TextField(
        modifier = Modifier.fillMaxWidth(),
        placeholderText = "PEER_ADDR:PORT",
        value = peerAddressValue.value,
        onValueChange = { newValue ->
          peerAddressValue.value = newValue
          model.onPeerAddressChanged(newValue.text)
        }
      )
      Spacer(Modifier.height(24.dp))
      Label(text = "Enter amount to fund (in sats)", type = LabelType.Body2Regular)
      Spacer(Modifier.height(24.dp))
      TextField(
        modifier = Modifier.fillMaxWidth(),
        placeholderText = "1234",
        value = fundingAmountValue.value,
        onValueChange = { newValue ->
          fundingAmountValue.value = newValue
          model.onFundingAmountChanged(newValue.text)
        }
      )

      Spacer(Modifier.height(24.dp))
      Button(
        text = "Connect and Open Channel",
        treatment = Primary,
        size = Footer,
        onClick = StandardClick(model.onConnectPressed)
      )

      Spacer(Modifier.height(24.dp))
      Divider()
      Spacer(Modifier.height(24.dp))
      Label(text = "Channel List", type = LabelType.Title3)
      Spacer(Modifier.height(24.dp))
      Divider()
    }

    items(model.channelsModel.channelOpenRows) {
      ChannelRow(model = it)
      Divider()
    }
  }
}

@Composable
private fun ChannelRow(model: ChannelOpenRowModel) {
  ListItem(
    modifier =
      Modifier
        .fillMaxWidth(),
    title = model.truncatedTxid(),
    secondaryText = model.channelSubtitleText,
    trailingAccessory = model.trailingAccessory,
    onClick = model.onClick
  )
}

@Preview
@Composable
internal fun ConnectAndOpenChannelScreenPreview() {
  PreviewWalletTheme {
    ConnectAndOpenChannelScreen(
      ConnectAndOpenChannelBodyModel(
        channelsModel =
          ChannelsModel(
            channelOpenRows =
              immutableListOf(
                ChannelOpenRowModel(
                  peerNodeId = "030c...c14f",
                  channelSubtitleText = "0/5 confirmations",
                  fundingTxId = "bf397c2f822bfe69a1b469da9db9190dea06561e3909db9782a4def73d18b69c",
                  trailingAccessory =
                    ListItemAccessory.IconAccessory(
                      model =
                        IconModel(
                          icon = Icon.SmallIconWarning,
                          iconSize = Small
                        )
                    ),
                  onClick = {}
                ),
                ChannelOpenRowModel(
                  peerNodeId = "03c2...1dda",
                  channelSubtitleText = "100000/0 msats",
                  fundingTxId = "df24a7fd89c1d7f78512a1da17c4d7002ea00fc0d7f087165a37ec30bbf15b35",
                  trailingAccessory =
                    ListItemAccessory.IconAccessory(
                      model =
                        IconModel(
                          icon = Icon.SmallIconWarning,
                          iconSize = Small
                        )
                    ),
                  onClick = {}
                )
              )
          ),
        peerNodeId = "",
        peerAddress = "",
        fundingAmount = "",
        onchainBalanceLabelString = "You have 10000 sats",
        onPeerNodeIdChanged = {},
        onPeerAddressChanged = {},
        onFundingAmountChanged = {},
        onConnectPressed = {},
        onBack = {}
      )
    )
  }
}
