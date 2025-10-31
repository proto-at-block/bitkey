package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessoryAlignment
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class PrivateWalletMigrationCompleteBodyModel(
  override val onBack: (() -> Unit),
) : FormBodyModel(
    id = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_COMPLETE,
    onBack = onBack,
    toolbar =
      ToolbarModel(
        leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(
          onClick = onBack
        )
      ),
    header = FormHeaderModel(
      icon = Icon.LargeIconCheckFilled,
      headline = "Your wallet update is complete",
      subline = "Take precautions to avoid sending money to your old wallet."
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          style = ListGroupStyle.DIVIDER,
          items = immutableListOf(
            ListItemModel(
              title = "Updated receive address",
              secondaryText = "Copy a new receive address every time you transfer bitcoin to avoid sending funds to an old wallet.",
              leadingAccessory = ListItemAccessory.IconAccessory(
                model = IconModel(
                  icon = Icon.SmallIconCheckStroked,
                  iconSize = IconSize.Small,
                  iconTint = IconTint.Foreground
                )
              ),
              leadingAccessoryAlignment = ListItemAccessoryAlignment.TOP
            ),
            ListItemModel(
              title = "Updated Emergency Exit Kit",
              secondaryText = "Your Emergency Exit Kit has been updated in your cloud account. Previous versions will no longer work.",
              leadingAccessory = ListItemAccessory.IconAccessory(
                model = IconModel(
                  icon = Icon.SmallIconRecovery,
                  iconSize = IconSize.Small,
                  iconTint = IconTint.Foreground
                )
              ),
              leadingAccessoryAlignment = ListItemAccessoryAlignment.TOP
            )
          )
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Got it",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = StandardClick(onBack)
    )
  )
