package build.wallet.statemachine.dev.lightning

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.model.alert.ButtonAlertModel

data class LightningDebugBodyModel(
  val nodeId: String,
  val spendableOnchainBalance: String,
  val fundingAlertModel: ButtonAlertModel?,
  override val onBack: () -> Unit,
  val onGetFundingAddressClicked: () -> Unit,
  val onSyncWalletClicked: () -> Unit,
  val onConnectAndOpenChannelButtonClicked: () -> Unit,
  val onSendAndReceivePaymentClicked: () -> Unit,
  // This is only used by the debug menu, it doesn't need a screen ID
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel()

fun fundingAddressAlertModel(
  subline: String = "",
  onDismiss: () -> Unit,
  onPrimaryButtonClick: () -> Unit,
): ButtonAlertModel =
  ButtonAlertModel(
    title = "Your funding address",
    subline = subline,
    onDismiss = onDismiss,
    primaryButtonText = "Copy to Clipboard",
    onPrimaryButtonClick = onPrimaryButtonClick,
    secondaryButtonText = "Cancel",
    onSecondaryButtonClick = onDismiss
  )
