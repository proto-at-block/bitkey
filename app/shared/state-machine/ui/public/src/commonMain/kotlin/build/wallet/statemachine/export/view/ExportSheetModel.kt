package build.wallet.statemachine.export.view

import build.wallet.analytics.events.screen.id.ExportToolsEventTrackerScreenId
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel

fun ExportWalletDescriptorSheetModel(
  onCtaClicked: () -> Unit,
  onClosed: () -> Unit,
) = ExportSheetModel(
  headline = "Export wallet descriptor",
  subline = "Download your Bitkey wallet descriptor",
  ctaString = "Download Xpub bundle",
  cancelString = "Cancel",
  onCtaClicked = onCtaClicked,
  onClosed = onClosed
)

fun ExportTransactionHistorySheetModel(
  onCtaClicked: () -> Unit,
  onClosed: () -> Unit,
) = ExportSheetModel(
  headline = "Export transaction history",
  subline = "Download your Bitkey transaction history.",
  ctaString = "Download .CSV",
  cancelString = "Cancel",
  onCtaClicked = onCtaClicked,
  onClosed = onClosed
)

private fun ExportSheetModel(
  headline: String,
  subline: String,
  ctaString: String,
  cancelString: String,
  onCtaClicked: () -> Unit,
  onClosed: () -> Unit,
) = SheetModel(
  body = FormBodyModel(
    onBack = onClosed,
    toolbar = null,
    header = FormHeaderModel(
      headline = headline,
      subline = subline
    ),
    primaryButton = ButtonModel(
      text = ctaString,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = SheetClosingClick(onCtaClicked)
    ),
    secondaryButton = ButtonModel(
      text = cancelString,
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = SheetClosingClick(onClosed)
    ),
    renderContext = RenderContext.Sheet,
    id = ExportToolsEventTrackerScreenId.EXPORT_WALLET_DESCRIPTOR_SHEET
  ),
  onClosed = onClosed
)
