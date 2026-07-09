package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentVerticalAlignment
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType

private const val W3_UPGRADE_BLOCKER_TITLE = "Get the next\ngeneration Bitkey"
private const val W3_UPGRADE_BLOCKER_SUBLINE =
  "You've trusted Bitkey from day one. Now, start verifying everything with the new Bitkey device."

data class W3UpgradeBlockerBodyModel(
  val onGetStarted: () -> Unit,
  val onClose: () -> Unit,
) : FormBodyModel(
    id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_BLOCKER,
    onBack = onClose,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(
        onClick = onClose
      )
    ),
    header = null,
    formScreenLayout = FormScreenLayoutModel.LargeTitle(
      scrollable = false,
      mainContentVerticalAlignment = FormMainContentVerticalAlignment.CENTER
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.Showcase(
        content = FormMainContentModel.Showcase.Content.ImageContent(
          image = FormMainContentModel.Showcase.Content.ImageContent.Image.UPGRADE_W3_UP_DOWN,
          scale = 1.15f
        ),
        fillAvailableSpace = false
      )
    ),
    preFooterContentList = immutableListOf(
      FormMainContentModel.HeaderBlock(
        header = FormHeaderModel(
          headline = W3_UPGRADE_BLOCKER_TITLE,
          subline = W3_UPGRADE_BLOCKER_SUBLINE,
          headlineLabelType = LabelType.Display3
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Get the new Bitkey",
      leadingIcon = Icon.ArrowUpRight,
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = StandardClick(onGetStarted)
    )
  )
