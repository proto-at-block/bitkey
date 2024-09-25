package build.wallet.statemachine.utxo

import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Error model shown to customer when only have 1 UTXO and therefore cannot perform UTXO consolidation
 * - at least 2 UTXOs is required.
 */
internal fun notEnoughUtxosToConsolidateErrorModel(onBack: () -> Unit): FormBodyModel {
  return ErrorFormBodyModel(
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack)
    ),
    title = "You're fully consolidated",
    subline = "There’s only 1 UTXO in your wallet, so you’re already fully consolidated. Nice.",
    primaryButton = ButtonDataModel(
      text = "Got it",
      onClick = onBack
    ),
    eventTrackerScreenId = UtxoConsolidationEventTrackerScreenId.NOT_ENOUGH_UTXOS_TO_CONSOLIDATE
  )
}
