package build.wallet.statemachine.utxo

import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconConsolidation
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.icon.IconBackgroundType.Circle
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Avatar
import build.wallet.ui.model.icon.IconSize.Large
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.icon.IconTint.Foreground
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Body model for the UTXO consolidation confirmation screen.
 *
 * Shown to the customer before they proceed with consolidating their UTXOs in settings.
 *
 * TODO(W-9563): polish UI
 */
fun utxoConsolidationConfirmationModel(
  balanceFiat: String,
  balanceBitcoin: String,
  utxoCount: String,
  consolidationCostFiat: String,
  consolidationCostBitcoin: String,
  onBack: () -> Unit,
  onConfirmClick: () -> Unit,
  onConsolidationTimeClick: () -> Unit,
  onConsolidationCostClick: () -> Unit,
) = FormBodyModel(
  id = UtxoConsolidationEventTrackerScreenId.UTXO_CONSOLIDATION_CONFIRMATION,
  onBack = onBack,
  header = FormHeaderModel(
    iconModel = IconModel(
      icon = SmallIconConsolidation,
      iconSize = Large,
      iconBackgroundType = Circle(circleSize = Avatar),
      iconTint = Foreground
    ),
    headline = "Consolidate UTXOs",
    subline = "Consolidate unspent bitcoin into a single UTXO to reduce future transaction fees.",
    sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
    alignment = CENTER
  ),
  toolbar = ToolbarModel(
    leadingAccessory =
      ToolbarAccessoryModel.ButtonAccessory(
        model =
          ButtonModel(
            text = "Cancel",
            treatment = TertiaryDestructive,
            size = Compact,
            onClick = StandardClick(onBack)
          )
      )
  ),
  mainContentList = immutableListOf(
    DataList(
      items = immutableListOf(
        Data(
          title = "Consolidation time",
          onTitle = onConsolidationTimeClick,
          titleIcon = IconModel(
            icon = Icon.SmallIconInformationFilled,
            iconSize = IconSize.XSmall,
            iconTint = IconTint.On30
          ),
          sideText = "~24 hours"
        )
      )
    ),
    DataList(
      items = immutableListOf(
        Data(
          title = "Wallet balance",
          sideText = balanceFiat,
          secondarySideText = balanceBitcoin
        ),
        Data(
          title = "Number of UTXOs",
          sideText = utxoCount
        ),
        Data(
          title = "Consolidation cost",
          onTitle = onConsolidationCostClick,
          titleIcon = IconModel(
            icon = Icon.SmallIconInformationFilled,
            iconSize = IconSize.XSmall,
            iconTint = IconTint.On30
          ),
          sideText = consolidationCostFiat,
          secondarySideText = consolidationCostBitcoin
        )
      )
    )
  ),
  primaryButton = ButtonModel(
    text = "Consolidate UTXOs",
    requiresBitkeyInteraction = true,
    onClick = onConfirmClick,
    treatment = Primary,
    size = Footer
  )
)

fun consolidationInfoSheetModel(
  eventTrackerScreenId: UtxoConsolidationEventTrackerScreenId,
  title: String,
  explainer: String,
  onBack: () -> Unit,
) = SheetModel(
  onClosed = onBack,
  dragIndicatorVisible = true,
  body = FormBodyModel(
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      headline = title,
      subline = explainer,
      alignment = CENTER
    ),
    primaryButton = ButtonModel(
      text = "Got it",
      size = Footer,
      onClick = SheetClosingClick(onBack)
    ),
    renderContext = Sheet,
    id = eventTrackerScreenId,
    eventTrackerShouldTrack = false
  )
)
