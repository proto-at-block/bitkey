package build.wallet.statemachine.utxo

import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * TODO(W-9563): polish UI
 */
data class UtxoConsolidationTransactionSentModel(
  val targetAddress: String,
  val arrivalTime: String,
  val utxosCountConsolidated: String,
  val consolidationCostBitcoin: String,
  val consolidationCostFiat: String,
  override val onBack: () -> Unit,
  val onDone: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = CloseAccessory(onBack)),
    header = FormHeaderModel(
      icon = Icon.LargeIconCheckFilled,
      headline = "Transaction sent",
      subline = targetAddress,
      sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
      alignment = CENTER
    ),
    mainContentList = immutableListOf(
      DataList(
        items = immutableListOf(
          Data(
            title = "Should arrive by",
            sideText = arrivalTime
          )
        )
      ),
      DataList(
        items = immutableListOf(
          Data(
            title = "UTXOs consolidated",
            sideText = utxosCountConsolidated
          ),
          Data(
            title = "Consolidation cost",
            sideText = consolidationCostFiat,
            secondarySideText = consolidationCostBitcoin
          )
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Done",
      onClick = StandardClick(onDone),
      size = Footer
    ),
    id = UtxoConsolidationEventTrackerScreenId.UTXO_CONSOLIDATION_TRANSACTION_SENT
  )
