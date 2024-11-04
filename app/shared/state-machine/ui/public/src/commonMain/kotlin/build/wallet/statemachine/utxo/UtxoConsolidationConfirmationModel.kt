package build.wallet.statemachine.utxo

import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.money.formatter.AmountDisplayText
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconConsolidation
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormMainContentModel.Callout
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.icon.IconBackgroundType.Circle
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Avatar
import build.wallet.ui.model.icon.IconSize.Large
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.icon.IconTint.Foreground
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import dev.zacsweers.redacted.annotations.Redacted

/**
 * Body model for the UTXO consolidation confirmation screen.
 *
 * Shown to the customer before they proceed with consolidating their UTXOs in settings.
 */
@Redacted
data class UtxoConsolidationConfirmationModel(
  val balanceTitle: String,
  val balanceAmountDisplayText: AmountDisplayText,
  val utxoCount: String,
  val consolidationCostDisplayText: AmountDisplayText,
  val estimatedConsolidationTime: String,
  val showUnconfirmedTransactionsCallout: Boolean,
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
  val onConsolidationTimeClick: () -> Unit,
  val onConsolidationCostClick: () -> Unit,
) : FormBodyModel(
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
    mainContentList = immutableListOfNotNull(
      Callout(
        item = CalloutModel(
          title = "Unconfirmed incoming transactions",
          subtitle = LabelModel
            .StringModel("To consolidate all UTXOs, wait until all incoming transactions are confirmed."),
          treatment = CalloutModel.Treatment.Warning,
          leadingIcon = Icon.SmallIconInformation
        )
      ).takeIf { showUnconfirmedTransactionsCallout },
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
            sideText = estimatedConsolidationTime
          )
        )
      ),
      DataList(
        items = immutableListOf(
          Data(
            title = balanceTitle,
            sideText = balanceAmountDisplayText.primaryAmountText,
            secondarySideText = balanceAmountDisplayText.secondaryAmountText
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
            sideText = consolidationCostDisplayText.primaryAmountText,
            secondarySideText = consolidationCostDisplayText.secondaryAmountText
          )
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      onClick = StandardClick(onContinue),
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
  body = ConsolidationInfoBodyModel(
    eventTrackerScreenId = eventTrackerScreenId,
    title = title,
    explainer = explainer,
    onBack = onBack
  )
)

private data class ConsolidationInfoBodyModel(
  val eventTrackerScreenId: UtxoConsolidationEventTrackerScreenId,
  val title: String,
  val explainer: String,
  override val onBack: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      headline = title,
      subline = explainer,
      alignment = LEADING
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
