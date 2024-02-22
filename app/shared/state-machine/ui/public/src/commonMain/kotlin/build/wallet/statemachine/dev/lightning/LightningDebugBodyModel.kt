package build.wallet.statemachine.dev.lightning

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

data class LightningDebugBodyModel(
  val nodeId: String,
  val spendableOnchainBalance: String,
  val fundingAlertModel: FundingAddressAlertModel?,
  override val onBack: () -> Unit,
  val onGetFundingAddressClicked: () -> Unit,
  val onSyncWalletClicked: () -> Unit,
  val onConnectAndOpenChannelButtonClicked: () -> Unit,
  val onSendAndReceivePaymentClicked: () -> Unit,
  // This is only used by the debug menu, it doesn't need a screen ID
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  data class FundingAddressAlertModel(
    val title: String = "Your funding address",
    val text: String = "",
    val dismissButtonTitle: String = "Cancel",
    val onDismiss: () -> Unit,
    val confirmButtonTitle: String = "Copy to Clipboard",
    val onConfirm: () -> Unit,
  )
}
