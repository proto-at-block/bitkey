package build.wallet.statemachine.recovery.socrec.add

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Screen to prompt the user to share their invite with their trusted contact.
 *
 * This should use the platform share sheet to share the invitation token.
 */
data class ShareInviteBodyModel(
  /**
   * The name of the trusted contact to use in the invitation.
   */
  val trustedContactName: String,
  /**
   * Boolean indicating whether we are inviting a beneficiary
   */
  val isBeneficiary: Boolean,
  /**
   * Invoked when the user has successfully invited their shared contact.
   */
  val onShareComplete: () -> Unit,
  /**
   * Invoked when the user navigates back.
   */
  val onBackPressed: () -> Unit,
) : FormBodyModel(
    id = if (isBeneficiary) {
      SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ENROLLMENT_SHARE_SCREEN
    } else {
      SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_SHARE_SCREEN
    },
    onBack = onBackPressed,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onBackPressed)
    ),
    header = FormHeaderModel(
      icon = if (!isBeneficiary) Icon.LargeIconShieldPerson else null,
      headline = "Finally, invite $trustedContactName" + (if (!isBeneficiary) " to be your Trusted Contact" else ""),
      subline =
        """
        To accept the invite, they’ll need to download the Bitkey app and enter your invite code.
        """.trimIndent()
    ),
    primaryButton = ButtonModel(
      text = "Share invite",
      treatment = ButtonModel.Treatment.Primary,
      leadingIcon = Icon.SmallIconShare,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onShareComplete)
    )
  )
