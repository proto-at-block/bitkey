package build.wallet.statemachine.trustedcontact.remove

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Builds a model prompting the removal of a Trusted Contact.
 *
 * @param trustedContactAlias The alias for the TC we're removing.
 * @param onRemove Invoked when the user confirms they want to try and  remove the trusted contact.
 * @param onClosed Invoked when the user closes the sheet.
 */
data class RemoveTrustedContactBodyModel(
  val trustedContactAlias: TrustedContactAlias,
  val isExpiredInvitation: Boolean,
  val onRemove: () -> Unit,
  val onClosed: () -> Unit,
  val isBeneficiary: Boolean,
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_REMOVAL_CONFIRMATION,
    onBack = onClosed,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onClick = onClosed)
    ),
    header = if (isExpiredInvitation) {
      FormHeaderModel(
        headline = "Your invitation to ${trustedContactAlias.alias} to be a " + (if (isBeneficiary) "beneficiary" else "Trusted Contact") + " has expired.",
        alignment = FormHeaderModel.Alignment.LEADING
      )
    } else {
      FormHeaderModel(
        headline = "Removing ${trustedContactAlias.alias} as a " + (if (isBeneficiary) "beneficiary" else "Trusted Contact") + " requires your Bitkey for approval.",
        subline = "Security-sensitive changes require your Bitkey to keep your wallet safe.",
        alignment = FormHeaderModel.Alignment.LEADING
      )
    },
    primaryButton = ButtonModel(
      text = if (isBeneficiary) "Remove beneficiary" else "Remove Trusted Contact",
      requiresBitkeyInteraction = isExpiredInvitation,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = onRemove
    ),
    secondaryButton = null,
    renderContext = RenderContext.Screen
  )
