package build.wallet.statemachine.utxo

import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon.SmallIconConsolidation
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Displays the fee details for speeding up a utxo consolidation
 */
data class UtxoConsolidationSpeedUpConfirmationModel(
  override val onBack: () -> Unit,
  val onCancel: () -> Unit,
  val recipientAddress: String,
  val transactionSpeedText: String,
  val originalConsolidationCost: String,
  val originalConsolidationCostSecondaryText: String?,
  val consolidationCostDifference: String,
  val consolidationCostDifferenceSecondaryText: String?,
  val totalConsolidationCost: String,
  val totalConsolidationCostSecondaryText: String?,
  val onConfirmClick: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    header = FormHeaderModel(
      iconModel = IconModel(
        icon = SmallIconConsolidation,
        iconSize = IconSize.Avatar
      ),
      headline = "Speed up your consolidation",
      subline = recipientAddress,
      sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
      alignment = CENTER
    ),
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.ButtonAccessory(
        model = ButtonModel(
          text = "Cancel",
          treatment = TertiaryDestructive,
          size = Compact,
          onClick = StandardClick(onCancel)
        )
      )
    ),
    mainContentList = immutableListOf(
      DataList(
        items = immutableListOf(
          DataList.Data(
            title = "New arrival time",
            sideText = transactionSpeedText
          )
        )
      ),
      DataList(
        items = immutableListOf(
          DataList.Data(
            title = "Original consolidation cost",
            sideText = originalConsolidationCost,
            secondarySideText = originalConsolidationCostSecondaryText
          ),
          DataList.Data(
            title = "Speed up fee",
            sideText = consolidationCostDifference,
            secondarySideText = consolidationCostDifferenceSecondaryText
          )
        ),
        total = DataList.Data(
          title = "Total cost",
          sideText = totalConsolidationCost,
          sideTextType = DataList.Data.SideTextType.BODY2BOLD,
          secondarySideText = totalConsolidationCostSecondaryText
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Consolidate UTXOs",
      requiresBitkeyInteraction = true,
      onClick = onConfirmClick,
      treatment = Primary,
      size = Footer
    ),
    id = UtxoConsolidationEventTrackerScreenId.UTXO_CONSOLIDATION_SPEED_UP_CONFIRMATION
  )
