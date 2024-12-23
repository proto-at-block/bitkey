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
 * Transaction success screen displayed when a claim is completed and funds
 * transfer has been started.
 */
internal data class InheritanceTransferSuccessScreenModel(
  override val onBack: () -> Unit,
  val onDone: () -> Unit = onBack,
  val recipientAddress: String,
  val amount: String,
  val fees: String,
  val netReceivePrimary: String,
  val netReceiveSecondary: String,
) : FormBodyModel(
    onBack = onBack,
    id = InheritanceEventTrackerScreenId.ClaimComplete,
    header = FormHeaderModel(
      icon = Icon.LargeIconCheckFilled,
      headline = "Inheritance Transferred",
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
      text = "Done",
      onClick = StandardClick(onDone),
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer
    )
  )
