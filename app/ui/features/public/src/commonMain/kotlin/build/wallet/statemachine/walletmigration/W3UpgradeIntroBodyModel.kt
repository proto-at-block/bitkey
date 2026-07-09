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
    formScreenTitle = w3UpgradeInstructionScreenTitle(
      title = "Upgrade to the new Bitkey"
    ),
    formScreenLayout = w3UpgradeInstructionLayout(),
    headerToMainContentSpacing = W3_UPGRADE_INSTRUCTION_HEADER_TO_MAIN_CONTENT_SPACING,
    header = w3UpgradeInstructionHeader(
      subline = "This process replaces your current Bitkey with a new device."
    ),
    mainContentList = immutableListOf(
      introInstructionListGroup()
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
        w3UpgradeInstructionListItem(
          title = "Confirm with your new Bitkey",
          secondaryText = "You'll need to pair the new Bitkey hardware device before you can start the upgrade process.",
          icon = Icon.Bitkey
        ),
        w3UpgradeInstructionListItem(
          title = "Small network fee required",
          secondaryText = "To upgrade your key, you'll need to initiate an on-chain transaction to complete the process.",
          icon = Icon.BitcoinStroked
        )
      )
    )
  )
