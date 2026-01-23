package build.wallet.statemachine.trustedcontact.view

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
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
 */
data class ViewingInvitationBodyModel(
  val invitation: Invitation,
  val isExpired: Boolean,
  val onRemove: () -> Unit,
  val onShare: () -> Unit,
  val onReinvite: () -> Unit,
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_INVITATION_DETAIL_SHEET,
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      icon = Icon.LargeIconShieldPerson,
      headline = invitation.trustedContactAlias.alias,
      subline = if (isExpired) {
        "Your ${invitation.label} invite has expired."
      } else {
        "Your ${invitation.label} invite is pending."
      },
      alignment = FormHeaderModel.Alignment.CENTER
    ),
    primaryButton = if (isExpired) {
      ButtonModel(
        text = "Reinvite",
        treatment = ButtonModel.Treatment.Primary,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick {
          onReinvite()
        }
      )
    } else {
      ButtonModel(
        text = "Share Invite",
        leadingIcon = Icon.SmallIconShare,
        treatment = ButtonModel.Treatment.Primary,
        size = ButtonModel.Size.Footer,
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
