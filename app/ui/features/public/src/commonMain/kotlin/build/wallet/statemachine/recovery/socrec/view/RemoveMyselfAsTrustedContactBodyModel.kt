package build.wallet.statemachine.recovery.socrec.view

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Full-screen modal prompting a W3 Trusted Contact to confirm removing themselves
 * as a Recovery Contact for another person's wallet. Requires Bitkey HW tap.
 *
 * @param protectedCustomerAlias Name of the person whose wallet you're protecting.
 * @param onRemove Invoked when the user confirms removal (proceeds to HW tap).
 * @param onClosed Invoked when the user dismisses the screen.
 */
data class RemoveMyselfAsTrustedContactBodyModel(
  val protectedCustomerAlias: String,
  val onRemove: () -> Unit,
  val onClosed: () -> Unit,
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_PROTECTED_CUSTOMER_REMOVAL_CONFIRMATION,
    onBack = onClosed,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onClick = onClosed)
    ),
    header = FormHeaderModel(
      headline = "Removing yourself as a Recovery Contact for $protectedCustomerAlias requires your Bitkey for approval.",
      subline = "Security-sensitive changes require your Bitkey to keep your wallet safe.",
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    primaryButton = ButtonModel(
      text = "Remove Myself as Recovery Contact",
      requiresBitkeyInteraction = true,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = onRemove
    ),
    secondaryButton = null,
    renderContext = RenderContext.Screen
  )
