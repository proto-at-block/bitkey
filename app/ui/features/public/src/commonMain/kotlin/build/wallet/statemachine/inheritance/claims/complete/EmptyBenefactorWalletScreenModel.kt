package build.wallet.statemachine.inheritance.claims.complete

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

internal data class EmptyBenefactorWalletScreenModel(
  val onClose: () -> Unit,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.ClaimEmpty,
    onBack = onClose,
    header = FormHeaderModel(
      headline = "There are no funds in your benefactor's wallet",
      subline = "Your inheritance claim was successful, however your benefactor’s wallet has no funds available to transfer. ",
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    toolbar = ToolbarModel(leadingAccessory = CloseAccessory(onClose)),
    primaryButton = ButtonModel(
      text = "Ok",
      onClick = StandardClick { onClose() },
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer
    )
  )
