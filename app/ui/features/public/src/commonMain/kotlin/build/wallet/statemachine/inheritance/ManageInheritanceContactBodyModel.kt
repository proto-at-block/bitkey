package build.wallet.statemachine.inheritance

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.RecoveryEntity
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

data class ManageInheritanceContactBodyModel(
  val onClose: () -> Unit,
  val onRemove: () -> Unit,
  val onShare: (Invitation) -> Unit,
  val recoveryEntity: RecoveryEntity,
  val claimControls: ClaimControls,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.ManageBenefactor,
    onBack = onClose,
    renderContext = RenderContext.Sheet,
    toolbar = null,
    header = FormHeaderModel(
      headline = when (recoveryEntity) {
        is ProtectedCustomer -> "Manage benefactor"
        else -> "Manage beneficiary"
      },
      subline = "You can " +
        when (recoveryEntity) {
          is Invitation -> "cancel the beneficiary invite, or resend to nudge."

          is TrustedContact -> when (claimControls) {
            is ClaimControls.Cancel -> "cancel an inheritance claim or remove this beneficiary."
            else -> "remove this beneficiary which will also cancel any active inheritance claims."
          }

          is ProtectedCustomer -> when (claimControls) {
            is ClaimControls.Complete -> "complete the inheritance claim or "
            is ClaimControls.Start -> "start an inheritance claim or "
            is ClaimControls.Cancel -> "cancel the inheritance claim or "
            ClaimControls.None -> ""
          } + "remove this benefactor."
        }
    ),
    primaryButton = when (claimControls) {
      is ClaimControls.Complete -> ButtonModel(
        text = "Complete inheritance claim",
        onClick = SheetClosingClick {
          claimControls.onClick()
        },
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Primary
      )
      is ClaimControls.Start -> ButtonModel(
        text = "Start inheritance claim",
        onClick = SheetClosingClick {
          claimControls.onClick()
        },
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Primary
      )
      is ClaimControls.Cancel -> ButtonModel(
        text = when (recoveryEntity) {
          is ProtectedCustomer -> "Cancel inheritance claim"
          else -> "Decline inheritance claim"
        },
        onClick = when (recoveryEntity) {
          is ProtectedCustomer -> StandardClick { claimControls.onClick() }
          else -> SheetClosingClick { claimControls.onClick() }
        },
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Primary
      )
      ClaimControls.None -> when (recoveryEntity) {
        is Invitation ->
          ButtonModel(
            text = "Resend invite",
            onClick = SheetClosingClick {
              onShare(recoveryEntity)
            },
            size = ButtonModel.Size.Footer,
            treatment = ButtonModel.Treatment.Primary
          )
        else -> null
      }
    },
    secondaryButton = ButtonModel(
      text = when (recoveryEntity) {
        is Invitation -> "Cancel invite"
        is TrustedContact -> "Remove beneficiary"
        is ProtectedCustomer -> "Remove benefactor"
      },
      onClick = StandardClick { onRemove() },
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.PrimaryDanger
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
