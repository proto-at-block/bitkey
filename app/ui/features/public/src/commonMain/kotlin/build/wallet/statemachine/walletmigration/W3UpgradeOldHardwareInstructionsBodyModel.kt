package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
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
    header = FormHeaderModel(
      headline = "Finish the upgrade using your first generation Bitkey",
      subline = "You'll need to tap your old Bitkey device a couple more times."
    ),
    mainContentList = immutableListOf(
      oldHardwareInstructionListGroup()
    ),
    designSystemV2Model = w3UpgradeInstructionDesignSystemV2Model(
      eyebrow = w3UpgradeStepEyebrow(4, totalSteps),
      title = "Finish the upgrade using your first generation Bitkey",
      subline = "You'll need to tap your old Bitkey device a couple more times.",
      mainContentList = immutableListOf(oldHardwareInstructionListGroupDesignSystemV2())
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
        w3UpgradeLegacyInstructionListItem(
          title = "Confirm the wallet upgrade",
          secondaryText = "Tap your old Bitkey to authorize the upgrade.",
          icon = Icon.SmallIconBitkey
        ),
        w3UpgradeLegacyInstructionListItem(
          title = "Move your funds to the new wallet",
          secondaryText = "Small network fee required.",
          icon = Icon.SmallIconBitkeySend
        )
      )
    )
  )

private fun oldHardwareInstructionListGroupDesignSystemV2() =
  FormMainContentModel.ListGroup(
    listGroupModel = ListGroupModel(
      style = ListGroupStyle.NONE,
      items = immutableListOf(
        w3UpgradeDesignSystemV2InstructionListItem(
          title = "Confirm the wallet upgrade",
          secondaryText = "Tap your old Bitkey to authorize the upgrade.",
          icon = Icon.SmallIconBitkey
        ),
        w3UpgradeDesignSystemV2InstructionListItem(
          title = "Move your funds to the new wallet",
          secondaryText = "Small network fee required.",
          icon = Icon.SmallIconBitkeySend
        )
      )
    )
  )
