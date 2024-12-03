package build.wallet.statemachine.utxo

import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon.Bitcoin
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Avatar
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import dev.zacsweers.redacted.annotations.Redacted

/**
 * Displays the fee details for speeding up a utxo consolidation
 */
@Redacted
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
        icon = Bitcoin,
        iconSize = Avatar
      ),
      headline = "Speed up your consolidation",
      subline = recipientAddress,
      sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
      alignment = LEADING
    ),
    toolbar = ToolbarModel(BackAccessory(onBack)),
    mainContentList = immutableListOf(
      FormMainContentModel.Divider,
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
          title = "Total",
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
