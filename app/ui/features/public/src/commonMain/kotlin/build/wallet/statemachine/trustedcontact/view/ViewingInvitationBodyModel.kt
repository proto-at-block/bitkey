package build.wallet.statemachine.trustedcontact.view

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.recovery.socrec.recoveryContactFormHeader
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

/**
 * Builds a body model to show details about an invitation.
 *
 * @param invitation The invitation to show information and actions for.
 * @param isExpired Whether the invitation is expired.
 * @param onRemove Invoked when the user wants to remove the trusted contact.
 * @param onShare Invoked when the user wants to reshare an existing invite.
 * @param onReinvite Invoke when the user wants to reinvite the trusted contact.
 * @param onBack Invoked when the user navigates back.
 * @param isCodeMissing True when the PAKE data needed to reconstruct this invite's code can no
 *   longer be found on this device. Reinvite and Share are both impossible in that case, so the
 *   primary action is suppressed and only the destructive Remove secondary action remains.
 * @param isCodeLoading True while the invite code is being loaded from local storage. The Share
 *   button is rendered as disabled-with-spinner until the lookup resolves to prevent sharing an
 *   empty code.
 */
data class ViewingInvitationBodyModel(
  val invitation: Invitation,
  val isExpired: Boolean,
  val onRemove: () -> Unit,
  val onShare: () -> Unit,
  val onReinvite: () -> Unit,
  override val onBack: () -> Unit,
  val isCodeMissing: Boolean = false,
  val isCodeLoading: Boolean = false,
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_INVITATION_DETAIL_SHEET,
    onBack = onBack,
    toolbar = null,
    header = if (invitation.isBeneficiary) {
      FormHeaderModel(
        icon = Icon.ShieldPerson,
        headline = invitation.trustedContactAlias.alias,
        subline = statusSubline(invitation, isExpired, isCodeMissing),
        alignment = FormHeaderModel.Alignment.CENTER
      )
    } else {
      recoveryContactFormHeader(
        headline = invitation.trustedContactAlias.alias,
        subline = statusSubline(invitation, isExpired, isCodeMissing)
      )
    },
    primaryButton = when {
      // When we have no invite code on this device we can't reinvite either (the existing
      // PAKE data is required to refresh the invite), so the only safe action is removal,
      // which we surface through the destructive secondary button below.
      isCodeMissing -> null
      isExpired ->
        ButtonModel(
          text = "Reinvite",
          treatment = ButtonModel.Treatment.Primary,
          size = ButtonModel.Size.Footer,
          onClick = StandardClick {
            onReinvite()
          }
        )
      else ->
        ButtonModel(
          text = "Share Invite",
          leadingIcon = Icon.Share,
          treatment = ButtonModel.Treatment.Primary,
          size = ButtonModel.Size.Footer,
          isLoading = isCodeLoading,
          isEnabled = !isCodeLoading,
          onClick = StandardClick {
            onShare()
          }
        )
    },
    secondaryButton = ButtonModel(
      "Remove ${invitation.label}", treatment = ButtonModel.Treatment.SecondaryDestructive,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onRemove)
    ),
    renderContext = RenderContext.Sheet
  )

/**
 * Inline label used in copy to refer to the contact.
 */
private val Invitation.label: String get() = when {
  TrustedContactRole.Beneficiary == roles.singleOrNull() -> "beneficiary"
  else -> "Recovery Contact"
}

private fun statusSubline(
  invitation: Invitation,
  isExpired: Boolean,
  isCodeMissing: Boolean,
): String {
  val label = invitation.label
  return when {
    isCodeMissing -> "We couldn't find an invite code for your $label. Remove them and send a new invite to try again."
    isExpired -> "Your $label invite has expired. Reinvite them to send a new code."
    else -> "Your $label invite is awaiting acceptance. Share the invite to send them the code."
  }
}

private val Invitation.isBeneficiary: Boolean get() =
  TrustedContactRole.Beneficiary == roles.singleOrNull()
