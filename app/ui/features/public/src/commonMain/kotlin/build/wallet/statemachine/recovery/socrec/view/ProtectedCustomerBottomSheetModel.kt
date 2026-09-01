package build.wallet.statemachine.recovery.socrec.view

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.ProtectedCustomerRelationshipStatus.ENDORSED
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.recovery.socrec.recoveryContactFormHeader
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

fun ProtectedCustomerBottomSheetModel(
  protectedCustomer: ProtectedCustomer,
  isRemoveSelfAsTrustedContactButtonLoading: Boolean,
  onHelpWithRecovery: () -> Unit,
  onRemoveSelfAsTrustedContact: () -> Unit,
  onClosed: () -> Unit,
) = SheetModel(
  onClosed = onClosed,
  body = ProtectedCustomerBottomSheetBodyModel(
    protectedCustomer = protectedCustomer,
    isRemoveSelfAsTrustedContactButtonLoading = isRemoveSelfAsTrustedContactButtonLoading,
    onHelpWithRecovery = onHelpWithRecovery,
    onRemoveSelfAsTrustedContact = onRemoveSelfAsTrustedContact,
    onClosed = onClosed
  )
)

private data class ProtectedCustomerBottomSheetBodyModel(
  val protectedCustomer: ProtectedCustomer,
  val isRemoveSelfAsTrustedContactButtonLoading: Boolean,
  val onHelpWithRecovery: () -> Unit,
  val onRemoveSelfAsTrustedContact: () -> Unit,
  val onClosed: () -> Unit,
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_PROTECTED_CUSTOMER_SHEET,
    onBack = onClosed,
    toolbar = null,
    header =
      recoveryContactFormHeader(
        headline = protectedCustomer.alias.alias,
        subline = if (protectedCustomer.relationshipStatus == ENDORSED) {
          "You’re currently protecting their wallet."
        } else {
          "You’ll be able to help with recovery after they finish setup."
        },
        alignment = FormHeaderModel.Alignment.CENTER
      ),
    primaryButton = if (protectedCustomer.relationshipStatus == ENDORSED) {
      ButtonModel(
        text = "Help with Recovery",
        size = ButtonModel.Size.Footer,
        onClick = SheetClosingClick(onHelpWithRecovery),
        treatment = ButtonModel.Treatment.Secondary
      )
    } else {
      null
    },
    secondaryButton =
      ButtonModel(
        text = "Remove Myself as Recovery Contact",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick { onRemoveSelfAsTrustedContact() },
        treatment = ButtonModel.Treatment.SecondaryDestructive,
        isLoading = isRemoveSelfAsTrustedContactButtonLoading
      ),
    renderContext = RenderContext.Sheet
  )
