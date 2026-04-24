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
 * "Upgrade to the new Bitkey" introduction screen.
 */
data class W3UpgradeIntroBodyModel(
  override val onBack: (() -> Unit)?,
  val onContinue: () -> Unit,
  val isLoading: Boolean = false,
) : FormBodyModel(
    id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_INTRO,
    onBack = onBack,
    toolbar = onBack?.let {
      ToolbarModel(
        leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(
          onClick = onBack
        )
      )
    },
    header = FormHeaderModel(
      headline = "Upgrade to the new Bitkey",
      subline = "This process replaces your current Bitkey with a new device."
    ),
    mainContentList = immutableListOf(
      introInstructionListGroup()
    ),
    designSystemV2Model = w3UpgradeInstructionDesignSystemV2Model(
      title = "Upgrade to the new Bitkey",
      subline = "This process replaces your current Bitkey with a new device.",
      mainContentList = immutableListOf(introInstructionListGroupDesignSystemV2())
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      isLoading = isLoading,
      onClick = StandardClick(onContinue)
    )
  )

private fun introInstructionListGroup() =
  FormMainContentModel.ListGroup(
    listGroupModel = ListGroupModel(
      style = ListGroupStyle.NONE,
      items = immutableListOf(
        w3UpgradeLegacyInstructionListItem(
          title = "Confirm with your new Bitkey",
          secondaryText = "You'll need to pair the new Bitkey hardware device before you can start the upgrade process.",
          icon = Icon.SmallIconBitkey
        ),
        w3UpgradeLegacyInstructionListItem(
          title = "Small network fee required",
          secondaryText = "To upgrade your key, you'll need to initiate an on-chain transaction to complete the process.",
          icon = Icon.SmallIconBitcoinStroked
        )
      )
    )
  )

private fun introInstructionListGroupDesignSystemV2() =
  FormMainContentModel.ListGroup(
    listGroupModel = ListGroupModel(
      style = ListGroupStyle.NONE,
      items = immutableListOf(
        w3UpgradeDesignSystemV2InstructionListItem(
          title = "Confirm with your new Bitkey",
          secondaryText = "You'll need to pair the new Bitkey hardware device before you can start the upgrade process.",
          icon = Icon.SmallIconBitkey
        ),
        w3UpgradeDesignSystemV2InstructionListItem(
          title = "Small network fee required",
          secondaryText = "To upgrade your key, you'll need to initiate an on-chain transaction to complete the process.",
          icon = Icon.SmallIconBitcoinStroked
        )
      )
    )
  )
