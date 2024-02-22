package build.wallet.ui.app.dev.lightning

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.dev.lightning.LightningDebugBodyModel
import build.wallet.statemachine.dev.lightning.LightningDebugBodyModel.FundingAddressAlertModel
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun LightningDebugScreen(model: LightningDebugBodyModel) {
  BackHandler(onBack = model.onBack)
  Column(
    modifier =
      Modifier
        .padding(horizontal = 20.dp)
        .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Toolbar(
      model =
        ToolbarModel(
          leadingAccessory = BackAccessory(onClick = model.onBack),
          middleAccessory = ToolbarMiddleAccessoryModel(title = "Lightning Debug")
        )
    )

    Spacer(Modifier.height(24.dp))
    Card {
      ListItem(
        title = "Node ID",
        sideText = model.nodeId
      )
      Divider()
      ListItem(
        title = "Spendable Onchain Balance",
        sideText = model.spendableOnchainBalance
      )
    }
    Spacer(Modifier.height(24.dp))
    Card {
      Spacer(Modifier.height(24.dp))
      Button(
        text = "Sync Wallets",
        treatment = Primary,
        size = Footer,
        onClick = Click.StandardClick { model.onSyncWalletClicked() }
      )
      Spacer(Modifier.height(24.dp))
      Button(
        text = "Get Funding Address",
        treatment = Primary,
        size = Footer,
        onClick = Click.StandardClick { model.onGetFundingAddressClicked() }
      )
      Spacer(Modifier.height(24.dp))
      Button(
        text = "Connect and Open Channel",
        treatment = Primary,
        size = Footer,
        onClick = Click.StandardClick { model.onConnectAndOpenChannelButtonClicked() }
      )
      Spacer(Modifier.height(24.dp))
      Button(
        text = "Send and Receive Payment",
        treatment = Primary,
        size = Footer,
        onClick = Click.StandardClick { model.onSendAndReceivePaymentClicked() }
      )
      Spacer(Modifier.height(24.dp))
    }

    model.fundingAlertModel?.let { alertModel ->
      FundingAddressAlert(model = alertModel)
    }
  }
}

@Composable
private fun FundingAddressAlert(model: FundingAddressAlertModel) {
  AlertDialog(
    title = model.title,
    subline = model.text,
    onDismiss = model.onDismiss,
    onPrimaryButtonClick = model.onConfirm,
    onSecondaryButtonClick = model.onDismiss,
    primaryButtonText = model.confirmButtonTitle.uppercase(),
    secondaryButtonText = model.dismissButtonTitle.uppercase()
  )
}

@Preview
@Composable
internal fun LightningDebugScreenPreview() {
  PreviewWalletTheme {
    LightningDebugScreen(
      LightningDebugBodyModel(
        nodeId = "test-node-id",
        spendableOnchainBalance = "100000",
        fundingAlertModel = null,
        onBack = {},
        onGetFundingAddressClicked = {},
        onSyncWalletClicked = {},
        onConnectAndOpenChannelButtonClicked = {},
        onSendAndReceivePaymentClicked = {}
      )
    )
  }
}

@Preview
@Composable
internal fun LightningDebugScreenPreviewWithFundingModal() {
  PreviewWalletTheme {
    LightningDebugScreen(
      LightningDebugBodyModel(
        nodeId = "test-node-id",
        spendableOnchainBalance = "100000",
        fundingAlertModel =
          FundingAddressAlertModel(
            text = "tb1qccrvx8k6wnf3glqwcvcnrzrd63vesg8etg2ctr",
            onDismiss = {},
            onConfirm = {}
          ),
        onBack = {},
        onGetFundingAddressClicked = {},
        onSyncWalletClicked = {},
        onConnectAndOpenChannelButtonClicked = {},
        onSendAndReceivePaymentClicked = {}
      )
    )
  }
}
