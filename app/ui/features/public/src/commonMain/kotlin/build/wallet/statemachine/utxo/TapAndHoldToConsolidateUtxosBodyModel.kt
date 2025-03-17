package build.wallet.statemachine.utxo

import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary

data class TapAndHoldToConsolidateUtxosBodyModel(
  override val onBack: () -> Unit,
  val onConsolidate: () -> Unit,
) : FormBodyModel(
    id = UtxoConsolidationEventTrackerScreenId.TAP_AND_HOLD_TO_CONSOLIDATE_SHEET,
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Tap and hold to consolidate",
      subline = "Consolidation takes a bit longer than a single transaction. You’ll need to hold your Bitkey to your device until the consolidation completes.",
      alignment = LEADING
    ),
    primaryButton = ButtonModel(
      text = "Consolidate UTXOs",
      requiresBitkeyInteraction = true,
      onClick = onConsolidate,
      treatment = Primary,
      size = Footer
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      size = Footer,
      treatment = Secondary,
      onClick = SheetClosingClick(onBack)
    ),
    renderContext = Sheet
  )
