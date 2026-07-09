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
 * "Finish the upgrade using your first generation Bitkey" screen.
 * Shown after the new W3 hardware is paired, before sweeping with old hardware.
 */
data class W3UpgradeOldHardwareInstructionsBodyModel(
  override val onBack: (() -> Unit)?,
  val onContinue: () -> Unit,
  val step: Int = 4,
  val totalSteps: Int = 4,
) : FormBodyModel(
    id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_OLD_HARDWARE_INSTRUCTIONS,
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
      title = "Finish the upgrade using your first generation Bitkey"
    ),
    formScreenLayout = w3UpgradeInstructionLayout(),
    headerToMainContentSpacing = W3_UPGRADE_INSTRUCTION_HEADER_TO_MAIN_CONTENT_SPACING,
    header = w3UpgradeInstructionHeader(
      subline = "You'll need to tap your old Bitkey device a couple more times."
    ),
    mainContentList = immutableListOf(
      oldHardwareInstructionListGroup()
    ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      text = "Continue",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onContinue)
    )
  )

private fun oldHardwareInstructionListGroup() =
  FormMainContentModel.ListGroup(
    listGroupModel = ListGroupModel(
      style = ListGroupStyle.NONE,
      items = immutableListOf(
        w3UpgradeInstructionListItem(
          title = "Confirm the wallet upgrade",
          secondaryText = "Tap your old Bitkey to authorize the upgrade.",
          icon = Icon.Bitkey
        ),
        w3UpgradeInstructionListItem(
          title = "Move your funds to the new wallet",
          secondaryText = "Small network fee required.",
          icon = Icon.BitkeySend
        )
      )
    )
  )
