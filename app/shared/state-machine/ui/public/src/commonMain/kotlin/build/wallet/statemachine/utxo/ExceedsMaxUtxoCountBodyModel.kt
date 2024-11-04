package build.wallet.statemachine.utxo

import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun ExceedsMaxUtxoCountBodyModel(
  onBack: () -> Unit,
  maxUtxoCount: Int,
  onContinue: () -> Unit,
) = ErrorFormBodyModel(
  toolbar = ToolbarModel(
    leadingAccessory = BackAccessory(onBack)
  ),
  title = "Multiple consolidations required",
  subline = "Due to NFC constraints, the maximum number of UTXOs that can be consolidated in a single transaction is $maxUtxoCount.\n\n" +
    "To consolidate all of your UTXOs, repeat this process as many times as necessary.",
  primaryButton = ButtonDataModel(
    text = "Got it",
    onClick = onContinue
  ),
  onBack = onBack,
  eventTrackerScreenId = UtxoConsolidationEventTrackerScreenId.UTXO_CONSOLIDATION_EXCEEDED_MAX_COUNT
)
