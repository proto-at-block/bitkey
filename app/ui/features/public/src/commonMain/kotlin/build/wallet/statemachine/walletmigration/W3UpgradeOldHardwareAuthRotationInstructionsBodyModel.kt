package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * "Tap your old Bitkey to authorize the upgrade" screen.
 * Shown before the auth key rotation NFC tap, prompting the user to use
 * their old (first generation) Bitkey device.
 */
data class W3UpgradeOldHardwareAuthRotationInstructionsBodyModel(
  override val onBack: (() -> Unit)?,
  val onContinue: () -> Unit,
  val onDeferExit: (() -> Unit)?,
  val step: Int = 2,
  val totalSteps: Int = 4,
) : FormBodyModel(
    id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_OLD_HARDWARE_AUTH_ROTATION_INSTRUCTIONS,
    onBack = onBack,
    toolbar = onBack?.let {
      ToolbarModel(
        leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(
          onClick = onBack
        )
      )
    },
    formScreenTitle = w3UpgradeInstructionScreenTitle(
      eyebrow = w3UpgradeStepEyebrow(step, totalSteps),
      title = "Tap your old Bitkey to finish the upgrade"
    ),
    formScreenLayout = w3UpgradeInstructionLayout(),
    headerToMainContentSpacing = W3_UPGRADE_INSTRUCTION_HEADER_TO_MAIN_CONTENT_SPACING,
    header = w3UpgradeInstructionHeader(
      subline = "Use your first generation Bitkey device to authorize the upgrade to your new device."
    ),
    mainContentList = immutableListOf(
      oldHardwareAuthRotationInstructionListGroup()
    ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      text = "Continue",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onContinue)
    ),
    secondaryButton = onDeferExit?.let {
      ButtonModel(
        text = "I don't have my old Bitkey",
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Secondary,
        onClick = StandardClick(it)
      )
    }
  )

private fun oldHardwareAuthRotationInstructionListGroup() =
  FormMainContentModel.ListGroup(
    listGroupModel = ListGroupModel(
      style = ListGroupStyle.NONE,
      items = immutableListOf(
        w3UpgradeInstructionListItem(
          title = "Have your old Bitkey ready",
          secondaryText = "You'll need to tap your first generation device to confirm.",
          icon = Icon.Bitkey
        )
      )
    )
  )
