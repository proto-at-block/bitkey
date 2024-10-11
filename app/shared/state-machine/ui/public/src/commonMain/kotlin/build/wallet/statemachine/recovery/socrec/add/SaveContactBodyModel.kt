package build.wallet.statemachine.recovery.socrec.add

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.Icon
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
data class SaveContactBodyModel(
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
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ADD_TC_HARDWARE_CHECK,
    onBack = onBackPressed,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onBackPressed)
    ),
    header = FormHeaderModel(
      icon = Icon.LargeIconShieldPerson,
      headline = "Save $trustedContactName as a Trusted Contact",
      subline = "Adding a Trusted Contact requires your Bitkey device since it impacts the security of your wallet."
    ),
    primaryButton = BitkeyInteractionButtonModel(
      text = "Save Trusted Contact",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onSave)
    )
  )
