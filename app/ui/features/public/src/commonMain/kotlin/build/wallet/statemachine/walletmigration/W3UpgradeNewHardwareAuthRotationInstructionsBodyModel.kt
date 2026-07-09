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
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * "Now tap your new Bitkey" screen.
 * Shown after the old-device authorization tap succeeds, prompting the user to switch to
 * their new W3 device to produce the auth-rotation signatures.
 */
data class W3UpgradeNewHardwareAuthRotationInstructionsBodyModel(
  override val onBack: (() -> Unit)?,
  val onContinue: () -> Unit,
  val step: Int = 3,
  val totalSteps: Int = 4,
) : FormBodyModel(
    id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_NEW_HARDWARE_AUTH_ROTATION_INSTRUCTIONS,
    onBack = onBack,
    toolbar = onBack?.let {
      ToolbarModel(
        leadingAccessory = build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.CloseAccessory(
          onClick = onBack
        )
      )
    },
    formScreenTitle = w3UpgradeInstructionScreenTitle(
      eyebrow = w3UpgradeStepEyebrow(step, totalSteps),
      title = "Now tap your new Bitkey"
    ),
    formScreenLayout = w3UpgradeInstructionLayout(),
    headerToMainContentSpacing = W3_UPGRADE_INSTRUCTION_HEADER_TO_MAIN_CONTENT_SPACING,
    header = w3UpgradeInstructionHeader(
      subline = "Use your new Bitkey device to complete the upgrade. This registers your new device with your account."
    ),
    mainContentList = immutableListOf(
      newHardwareAuthRotationInstructionListGroup()
    ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      text = "Continue",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onContinue)
    )
  )

private fun newHardwareAuthRotationInstructionListGroup() =
  FormMainContentModel.ListGroup(
    listGroupModel = ListGroupModel(
      style = ListGroupStyle.NONE,
      items = immutableListOf(
        w3UpgradeInstructionListItem(
          title = "Have your new Bitkey ready",
          secondaryText = "You'll need to tap your new device to confirm.",
          icon = Icon.Bitkey
        )
      )
    )
  )
