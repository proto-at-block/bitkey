package build.wallet.statemachine.recovery.socrec.view

import build.wallet.bitkey.relationships.Invitation
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
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
fun ViewingInvitationBodyModel(
  invitation: Invitation,
  isExpired: Boolean,
  onRemove: () -> Unit,
  onShare: () -> Unit,
  onReinvite: () -> Unit,
  onBack: () -> Unit,
) = FormBodyModel(
  id = null,
  onBack = onBack,
  toolbar = null,
  header =
    FormHeaderModel(
      icon = Icon.LargeIconShieldPerson,
      headline = invitation.trustedContactAlias.alias,
      subline =
        if (isExpired) {
          "Your Trusted Contact invite has expired."
        } else {
          "Your Trusted Contact invite is pending."
        },
      alignment = FormHeaderModel.Alignment.CENTER
    ),
  primaryButton =
    if (isExpired) {
      ButtonModel(
        text = "Reinvite",
        treatment = ButtonModel.Treatment.Primary,
        size = ButtonModel.Size.Footer,
        onClick =
          StandardClick {
            onReinvite()
          }
      )
    } else {
      ButtonModel(
        text = "Share Invite",
        leadingIcon = Icon.SmallIconShare,
        treatment = ButtonModel.Treatment.Primary,
        size = ButtonModel.Size.Footer,
        onClick =
          StandardClick {
            onShare()
          }
      )
    },
  secondaryButton =
    ButtonModel(
      "Remove Trusted Contact", treatment = ButtonModel.Treatment.SecondaryDestructive,
      size = ButtonModel.Size.Footer,
      onClick = SheetClosingClick(onRemove)
    ),
  renderContext = RenderContext.Sheet
)
