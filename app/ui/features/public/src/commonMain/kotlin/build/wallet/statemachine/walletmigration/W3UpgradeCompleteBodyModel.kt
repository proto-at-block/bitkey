package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.designSystemV2HeroIconHeader
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.market.MarketIcons

/**
 * "Your upgrade is complete" success screen.
 */
data class W3UpgradeCompleteBodyModel(
  override val onBack: () -> Unit,
  val onDone: () -> Unit,
) : FormBodyModel(
    id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_COMPLETE,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(
        onClick = onBack
      )
    ),
    header = FormHeaderModel(
      icon = Icon.LargeIconCheckFilled,
      headline = "Your upgrade is complete",
      subline = "You're ready to start using your new Bitkey device."
    ),
    designSystemV2Model = FormDesignSystemV2Model(
      header = designSystemV2HeroIconHeader(
        headline = "Your upgrade is complete",
        subline = "You're ready to start using your new Bitkey device.",
        icon = MarketIcons.Checkmark
      )
    ),
    mainContentList = immutableListOf(),
    primaryButton = ButtonModel(
      text = "Done",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = StandardClick(onDone)
    )
  )
