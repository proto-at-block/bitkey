package build.wallet.statemachine.inheritance

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel

data class ManageBenefactorBodyModel(
  val onClose: () -> Unit,
  val onRemoveBenefactor: () -> Unit,
  val claimControls: ClaimControls,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.ManageBenefactor,
    onBack = onClose,
    renderContext = RenderContext.Sheet,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Manage Benefactor",
      subline = "Manage your benefactor relationship."
    ),
    primaryButton = when (claimControls) {
      is ClaimControls.Complete -> ButtonModel(
        text = "Complete Inheritance Claim",
        onClick = SheetClosingClick {
          claimControls.onClick()
        },
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Primary
      )
      is ClaimControls.Start -> ButtonModel(
        text = "Start Inheritance Claim",
        onClick = SheetClosingClick {
          claimControls.onClick()
        },
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Primary
      )
      is ClaimControls.Cancel -> ButtonModel(
        text = "Cancel Inheritance Claim",
        onClick = SheetClosingClick {
          claimControls.onClick()
        },
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.PrimaryDanger
      )
      ClaimControls.None -> null
    },
    secondaryButton = ButtonModel(
      text = "Remove Benefactor",
      onClick = SheetClosingClick { onRemoveBenefactor() },
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.SecondaryDestructive
    ),
    tertiaryButton = ButtonModel(
      text = "Cancel",
      onClick = SheetClosingClick {
        onClose()
      },
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary
    )
  ) {
  sealed interface ClaimControls {
    data class Start(val onClick: () -> Unit) : ClaimControls

    data class Complete(val onClick: () -> Unit) : ClaimControls

    data class Cancel(val onClick: () -> Unit) : ClaimControls

    data object None : ClaimControls
  }
}
