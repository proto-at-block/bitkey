package build.wallet.statemachine.utxo

import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Error model shown to customer when they do not have any UTXOs to perform UTXO consolidation.
 */
internal fun noUtxosToConsolidateErrorModel(onBack: () -> Unit): FormBodyModel {
  return ErrorFormBodyModel(
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack)
    ),
    title = "There are no confirmed UTXOs to consolidate in this wallet",
    primaryButton = ButtonDataModel(
      text = "Got it",
      onClick = onBack
    ),
    eventTrackerScreenId = UtxoConsolidationEventTrackerScreenId.NO_UTXOS_TO_CONSOLIDATE
  )
}
