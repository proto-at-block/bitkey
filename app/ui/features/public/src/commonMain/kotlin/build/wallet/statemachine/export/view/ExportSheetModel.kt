package build.wallet.statemachine.export.view

import build.wallet.analytics.events.screen.id.ExportToolsEventTrackerScreenId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.Callout
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.callout.CalloutModel.Treatment.Warning

fun exportWalletDescriptorLoadingSheetModel(onClosed: () -> Unit) =
  exportSheetModel(
    headline = "Export wallet descriptor",
    subline = "Download your Bitkey wallet descriptor.",
    ctaString = "Download XPUB bundle",
    cancelString = "Cancel",
    isLoading = true,
    calloutModel = CalloutModel(
      subtitle = StringModel("XPUB bundles contain sensitive privacy data. For tax reporting, use your transaction history."),
      treatment = Warning
    ),
    onCtaClicked = {},
    onClosed = onClosed
  )

fun exportWalletDescriptorSheetModel(
  onClick: () -> Unit,
  onClosed: () -> Unit,
) = exportSheetModel(
  headline = "Export wallet descriptor",
  subline = "Download your Bitkey wallet descriptor.",
  ctaString = "Download XPUB bundle",
  cancelString = "Cancel",
  calloutModel = CalloutModel(
    subtitle = StringModel("XPUB bundles contain sensitive privacy data. For tax reporting, use your transaction history."),
    treatment = Warning
  ),
  onCtaClicked = onClick,
  onClosed = onClosed
)

fun exportTransactionHistoryLoadingSheetModel(onClosed: () -> Unit) =
  exportSheetModel(
    headline = "Export transaction history",
    subline = "Download your Bitkey transaction history.",
    ctaString = "Download .CSV",
    cancelString = "Cancel",
    isLoading = true,
    onCtaClicked = {},
    onClosed = onClosed
  )

fun exportTransactionHistorySheetModel(
  onCtaClicked: () -> Unit,
  onClosed: () -> Unit,
) = exportSheetModel(
  headline = "Export transaction history",
  subline = "Download your Bitkey transaction history.",
  ctaString = "Download .CSV",
  cancelString = "Cancel",
  onCtaClicked = onCtaClicked,
  onClosed = onClosed
)

fun encounteredErrorSheetModel(onClosed: () -> Unit) =
  exportSheetModel(
    headline = "An error occurred.",
    subline = "We had trouble exporting your wallet information. Please try again later.",
    ctaString = "Ok",
    cancelString = null,
    onCtaClicked = onClosed,
    onClosed = onClosed
  )

private fun exportSheetModel(
  headline: String,
  subline: String,
  ctaString: String,
  isLoading: Boolean = false,
  calloutModel: CalloutModel? = null,
  cancelString: String?,
  onCtaClicked: () -> Unit,
  onClosed: () -> Unit,
) = SheetModel(
  body = ExportSheetBodyModel(
    headline = headline,
    subline = subline,
    ctaString = ctaString,
    isLoading = isLoading,
    calloutModel = calloutModel,
    cancelString = cancelString,
    onCtaClicked = onCtaClicked,
    onClosed = onClosed
  ),
  onClosed = onClosed
)

data class ExportSheetBodyModel(
  val headline: String,
  val subline: String,
  val ctaString: String,
  val isLoading: Boolean,
  val cancelString: String?,
  val calloutModel: CalloutModel? = null,
  val onCtaClicked: () -> Unit,
  val onClosed: () -> Unit,
) : FormBodyModel(
    onBack = onClosed,
    toolbar = null,
    header = FormHeaderModel(
      headline = headline,
      subline = subline
    ),
    mainContentList = calloutModel?.let { immutableListOf(Callout(item = it)) }
      ?: emptyImmutableList(),
    primaryButton = ButtonModel(
      text = ctaString,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      isLoading = isLoading,
      onClick = StandardClick(onCtaClicked)
    ),
    secondaryButton = cancelString?.let {
      ButtonModel(
        text = it,
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer,
        onClick = SheetClosingClick(onClosed)
      )
    },
    renderContext = RenderContext.Sheet,
    id = ExportToolsEventTrackerScreenId.EXPORT_WALLET_DESCRIPTOR_SHEET
  )
