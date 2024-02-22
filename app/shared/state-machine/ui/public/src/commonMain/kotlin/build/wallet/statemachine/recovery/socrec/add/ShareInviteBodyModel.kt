package build.wallet.statemachine.recovery.socrec.add

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Screen to prompt the user to share their invite with their trusted contact.
 *
 * This should use the platform share sheet to share the invitation token.
 */
fun ShareInviteBodyModel(
  /**
   * The name of the trusted contact to use in the invitation.
   */
  trustedContactName: String,
  /**
   * Invoked when the user has successfully invited their shared contact.
   */
  onShareComplete: () -> Unit,
  /**
   * Invoked when the user navigates back.
   */
  onBackPressed: () -> Unit,
) = FormBodyModel(
  id = SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_SHARE_SCREEN,
  onBack = onBackPressed,
  toolbar = ToolbarModel(),
  header =
    FormHeaderModel(
      icon = Icon.LargeIconShieldPerson,
      headline = "Finally, invite $trustedContactName to be your Trusted Contact",
      subline =
        """
        To accept the invite, they’ll need to download the Bitkey app and enter your invite code.
        """.trimIndent()
    ),
  primaryButton =
    ButtonModel(
      text = "Share invite",
      treatment = ButtonModel.Treatment.Primary,
      leadingIcon = Icon.SmallIconShare,
      size = ButtonModel.Size.Footer,
      onClick =
        Click.standardClick {
          onShareComplete()
        }
    )
)
