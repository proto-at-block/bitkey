package build.wallet.statemachine.trustedcontact.reinvite

import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Prompt the user to save their trusted contact with bitkey.
 */
data class ReinviteContactBodyModel(
  /**
   * Name of the trusted contact to be added.
   */
  val trustedContactName: String,
  /**
   * Invoked when the user agrees to save with bitkey.
   */
  val onSave: () -> Unit,
  /**
   * Invoked when the user navigates back.
   */
  val onBackPressed: () -> Unit,
  /**
   * If this is a beneficiary invite.
   */
  val isBeneficiary: Boolean = false,
) : FormBodyModel(
    id = null,
    onBack = onBackPressed,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onClick = onBackPressed)
    ),
    header = FormHeaderModel(
      headline = "Resend invite to ${trustedContactName.trim()}",
      subline = "Reinviting a " + (if (isBeneficiary) "beneficiary" else "Trusted Contact") + " requires your Bitkey device since it impacts the security of your wallet."
    ),
    primaryButton = BitkeyInteractionButtonModel(
      text = "Resend invite",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick {
        onSave()
      }
    )
  )
