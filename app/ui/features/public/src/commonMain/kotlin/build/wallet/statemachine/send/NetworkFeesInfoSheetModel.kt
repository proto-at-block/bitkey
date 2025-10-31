package build.wallet.statemachine.send

import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

private val FEES_EXPLAINER =
  """
  Network fees are paid to miners for validating and adding transactions to the blockchain. The higher the fee, the more likely your transaction will be processed quickly.
  
  Wallet providers, including Bitkey, do not receive any portion of network fees.
  """.trimIndent()

data class NetworkFeesInfoSheetModel(
  override val onBack: () -> Unit,
  /**
   * If this explainer is being shown from another sheet, set this to true to ensure onBack behavior
   * is correct.
   */
  val fromSheet: Boolean = false,
) : FormBodyModel(
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Network fees",
      subline = FEES_EXPLAINER,
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    primaryButton = ButtonModel(
      text = "Got it",
      size = ButtonModel.Size.Footer,
      onClick = if (fromSheet) StandardClick(onBack) else SheetClosingClick(onBack)
    ),
    renderContext = RenderContext.Sheet,
    id = SendEventTrackerScreenId.SEND_NETWORK_FEES_INFO_SHEET,
    eventTrackerShouldTrack = false
  )
