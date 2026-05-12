package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
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
    mainContentList = immutableListOf(
      FormMainContentModel.Showcase(
        content = FormMainContentModel.Showcase.Content.ImageContent(
          image = FormMainContentModel.Showcase.Content.ImageContent.Image.UPGRADE_W3_UP_DOWN,
          scale = 1.15f
        ),
        title = W3_UPGRADE_BLOCKER_TITLE,
        body = build.wallet.statemachine.core.LabelModel.StringModel(
          W3_UPGRADE_BLOCKER_SUBLINE
        ),
        fillAvailableSpace = false
      )
    ),
    primaryButton = ButtonModel(
      text = "Get the new Bitkey",
      leadingIcon = Icon.SmallIconArrowUpRight,
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = StandardClick(onGetStarted)
    ),
    designSystemV2Model = FormDesignSystemV2Model(
      useDesignSystemV2ScreenLayout = true,
      scrollable = false,
      mainContentVerticalAlignment = FormDesignSystemV2Model.MainContentVerticalAlignment.CENTER,
      mainContentList = immutableListOf(
        FormMainContentModel.Showcase(
          content = FormMainContentModel.Showcase.Content.ImageContent(
            image = FormMainContentModel.Showcase.Content.ImageContent.Image.UPGRADE_W3_UP_DOWN,
            scale = 1.15f
          ),
          fillAvailableSpace = false
        )
      ),
      preFooterMainContentList = immutableListOf(
        FormMainContentModel.HeaderBlock(
          header = FormHeaderModel(
            headline = W3_UPGRADE_BLOCKER_TITLE,
            subline = W3_UPGRADE_BLOCKER_SUBLINE,
            headlineLabelType = LabelType.Display3
          )
        )
      )
    )
  )
