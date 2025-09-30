package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
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
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class PrivateWalletMigrationIntroBodyModel(
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
  val onLearnMore: () -> Unit,
) : FormBodyModel(
    id = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_INTRO,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(
        onClick = onBack
      )
    ),
    header = FormHeaderModel(
      headline = "Upgrade to an enhanced privacy wallet",
      sublineModel = LabelModel.LinkSubstringModel.from(
        substringToOnClick = mapOf(
          "Learn more" to onLearnMore
        ),
        string = "Use Bitkey without ever revealing your descriptor — your balance and any transaction made with your two keys is private, even from Bitkey servers. Learn more",
        underline = true,
        bold = false
      ),
      sublineTreatment = FormHeaderModel.SublineTreatment.REGULAR
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          header = "How it works",
          style = ListGroupStyle.NONE,
          headerTreatment = ListGroupModel.HeaderTreatment.SECONDARY,
          items = immutableListOf(
            ListItemModel(
              title = "Confirm with your Bitkey",
              secondaryText = "To keep your bitcoin secure, your Bitkey device is required for any security changes.",
              leadingAccessory = ListItemAccessory.IconAccessory(
                model = IconModel(
                  icon = Icon.SmallIconBitkey,
                  iconSize = IconSize.Small,
                  iconTint = IconTint.Foreground
                )
              )
            ),
            ListItemModel(
              title = "Network fees apply",
              secondaryText = "To upgrade your wallet, your balance will be transferred on-chain. Network fees apply.",
              leadingAccessory = ListItemAccessory.IconAccessory(
                model = IconModel(
                  icon = Icon.SmallIconBitcoinStroked,
                  iconSize = IconSize.Small,
                  iconTint = IconTint.Foreground
                )
              )
            )
          )
        )
      )
    ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      text = "Continue",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onContinue)
    )
  )
