package build.wallet.statemachine.inheritance.claims.complete

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Transaction Confirmation Screen for inheritance transfer.
 *
 * This screen is slightly different from a typical transaction confirmation
 * screen, as it shows the total amount, and does not go through priority
 * selection.
 */
internal data class InheritanceTransferConfirmationScreenModel(
  override val onBack: () -> Unit,
  val onTransfer: () -> Unit,
  val recipientAddress: String,
  val amount: String,
  val fees: String,
  val netReceivePrimary: String,
  val netReceiveSecondary: String,
) : FormBodyModel(
    onBack = onBack,
    id = InheritanceEventTrackerScreenId.ConfirmClaimTransfer,
    header = FormHeaderModel(
      icon = Icon.Bitcoin,
      headline = "Confirm inheritance transfer",
      subline = recipientAddress,
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    toolbar = ToolbarModel(leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onBack)),
    mainContentList = inheritanceConfirmationContent(
      amount = amount,
      fees = fees,
      netReceivePrimary = netReceivePrimary,
      netReceiveSecondary = netReceiveSecondary
    ),
    primaryButton = ButtonModel(
      text = "Transfer",
      onClick = StandardClick(onTransfer),
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer
    )
  )
