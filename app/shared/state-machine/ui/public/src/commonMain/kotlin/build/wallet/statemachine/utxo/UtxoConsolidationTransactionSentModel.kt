package build.wallet.statemachine.utxo

import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.formatter.AmountDisplayText
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import dev.zacsweers.redacted.annotations.Redacted

@Redacted
data class UtxoConsolidationTransactionSentModel(
  val targetAddress: String,
  val arrivalTime: String,
  val utxosCountConsolidated: String,
  val consolidationCostDisplayText: AmountDisplayText,
  override val onBack: () -> Unit,
  val onDone: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = CloseAccessory(onBack)),
    header = FormHeaderModel(
      icon = Icon.LargeIconCheckFilled,
      headline = "Consolidation started",
      subline = targetAddress,
      sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
      alignment = LEADING
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.Divider,
      DataList(
        items = immutableListOf(
          Data(
            title = "Arrival time",
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
            sideText = consolidationCostDisplayText.primaryAmountText,
            secondarySideText = consolidationCostDisplayText.secondaryAmountText
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
