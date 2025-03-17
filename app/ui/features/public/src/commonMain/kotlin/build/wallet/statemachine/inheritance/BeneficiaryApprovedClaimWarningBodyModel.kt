package build.wallet.statemachine.inheritance

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize

data class BeneficiaryApprovedClaimWarningBodyModel(
  val onTransferFunds: () -> Unit,
  val onClose: () -> Unit,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.BeneficiaryApprovedClaimWarning,
    renderContext = RenderContext.Sheet,
    onBack = onClose,
    toolbar = null,
    header = FormHeaderModel(
      iconModel = IconModel(
        icon = Icon.LargeIconWarning,
        iconSize = IconSize.Avatar
      ),
      headline = "Transfer funds to remove benefactor",
      subline = "Funds must be transferred before the benefactor can be removed."
    ),
    mainContentList = emptyImmutableList(),
    primaryButton = ButtonModel(
      text = "Transfer funds",
      onClick = SheetClosingClick {
        onTransferFunds()
      },
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary
    ),
    secondaryButton = ButtonModel(
      text = "OK",
      onClick = SheetClosingClick {
        onClose()
      },
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary
    )
  )
