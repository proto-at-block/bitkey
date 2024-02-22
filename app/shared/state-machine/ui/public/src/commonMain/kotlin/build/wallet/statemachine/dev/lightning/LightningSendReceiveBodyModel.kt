package build.wallet.statemachine.dev.lightning

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

data class LightningSendReceiveBodyModel(
  val amountToReceive: String,
  val invoiceInputValue: String,
  val lightningBalance: String,
  val generatedInvoiceString: String?,
  val onAmountToReceiveChanged: (String) -> Unit,
  val onLightningInvoiceChanged: (String) -> Unit,
  val handleSendButtonPressed: () -> Unit,
  val handleGenerateInvoicePressed: () -> Unit,
  override val onBack: () -> Unit,
  // This is only used by the debug menu, it doesn't need a screen ID
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel()
