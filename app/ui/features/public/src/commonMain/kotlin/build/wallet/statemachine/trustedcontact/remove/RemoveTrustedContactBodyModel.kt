package build.wallet.statemachine.trustedcontact.remove

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.FAILED
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.PAKE_DATA_UNAVAILABLE
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.TAMPERED
import build.wallet.bitkey.relationships.UnendorsedTrustedContact
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
  val onRemove: () -> Unit,
  val onClosed: () -> Unit,
  val isBeneficiary: Boolean,
  val removalContext: RemovalContext,
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_REMOVAL_CONFIRMATION,
    onBack = onClosed,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onClick = onClosed)
    ),
    header = FormHeaderModel(
      headline = removalContext.headline(
        trustedContactAlias = trustedContactAlias,
        isBeneficiary = isBeneficiary
      ),
      subline = removalContext.subline,
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    primaryButton = ButtonModel(
      text = removalContext.primaryButtonText(isBeneficiary),
      requiresBitkeyInteraction = removalContext != RemovalContext.ExpiredInvitation,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = onRemove
    ),
    secondaryButton = null,
    renderContext = RenderContext.Screen
  )

enum class RemovalContext {
  ActiveRelationship,
  ExpiredInvitation,
  FailedSetup,
  InvalidSetup,
}

internal fun TrustedContact.removalContext(isExpiredInvitation: Boolean): RemovalContext {
  if (isExpiredInvitation) {
    return RemovalContext.ExpiredInvitation
  }

  return when ((this as? UnendorsedTrustedContact)?.authenticationState) {
    FAILED, PAKE_DATA_UNAVAILABLE -> RemovalContext.FailedSetup
    TAMPERED -> RemovalContext.InvalidSetup
    else -> RemovalContext.ActiveRelationship
  }
}

private val RemovalContext.subline: String?
  get() =
    when (this) {
      RemovalContext.ActiveRelationship, RemovalContext.InvalidSetup ->
        "Security-sensitive changes require your Bitkey to keep your wallet safe."
      RemovalContext.FailedSetup ->
        "This removes the failed setup attempt. To add them later, invite them again. Security-sensitive changes require your Bitkey."
      RemovalContext.ExpiredInvitation -> null
    }

private fun RemovalContext.headline(
  trustedContactAlias: TrustedContactAlias,
  isBeneficiary: Boolean,
): String {
  val role = if (isBeneficiary) "beneficiary" else "Recovery Contact"
  return when (this) {
    RemovalContext.ActiveRelationship ->
      "Removing ${trustedContactAlias.alias} as a $role requires your Bitkey for approval."
    RemovalContext.ExpiredInvitation ->
      "Your invitation to ${trustedContactAlias.alias} to be a $role has expired."
    RemovalContext.FailedSetup ->
      "Remove failed $role setup for ${trustedContactAlias.alias}?"
    RemovalContext.InvalidSetup ->
      "Remove invalid $role for ${trustedContactAlias.alias}?"
  }
}

private fun RemovalContext.primaryButtonText(isBeneficiary: Boolean): String =
  when (this) {
    RemovalContext.FailedSetup -> "Remove setup"
    else -> if (isBeneficiary) "Remove beneficiary" else "Remove Recovery Contact"
  }
