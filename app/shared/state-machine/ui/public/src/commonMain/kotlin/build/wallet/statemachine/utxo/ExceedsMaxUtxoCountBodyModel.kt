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
  title = "You have more than $maxUtxoCount UTXOs.",
  subline = "You can only consolidate a maximum of $maxUtxoCount UTXOs" +
    " at one time. To consolidate all of your UTXOs you’ll have to do multiple consolidations." +
    " Continue to complete the first and you can return to do the rest.",
  primaryButton = ButtonDataModel(
    text = "Continue",
    onClick = onContinue
  ),
  onBack = onBack,
  eventTrackerScreenId = UtxoConsolidationEventTrackerScreenId.UTXO_CONSOLIDATION_EXCEEDED_MAX_COUNT
)
