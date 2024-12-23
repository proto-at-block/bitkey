package build.wallet.statemachine.account

import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Content for account access options for the regular, non-EAK app variant.
 */
data class AccountAccessMoreOptionsFormBodyModel(
  override val onBack: () -> Unit,
  val onRestoreYourWalletClick: (() -> Unit),
  val onBeTrustedContactClick: (() -> Unit),
  val onResetExistingDevice: (() -> Unit)?,
) : FormBodyModel(
    id = GeneralEventTrackerScreenId.ACCOUNT_ACCESS_MORE_OPTIONS,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onBack)),
    header = FormHeaderModel(
      headline = "Welcome to Bitkey",
      subline = "How do you want to get started?"
    ),
    mainContentList = immutableListOf(
      ListGroup(
        listGroupModel = ListGroupModel(
          items = immutableListOfNotNull(
            ListItemModel(
              leadingAccessory = IconAccessory(
                iconPadding = 12,
                model = IconModel(
                  icon = Icon.SmallIconShieldPerson,
                  iconSize = IconSize.Small
                )
              ),
              title = "Be a Trusted Contact",
              onClick = onBeTrustedContactClick,
              trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30)
            ),
            ListItemModel(
              leadingAccessory = IconAccessory(
                iconPadding = 12,
                model = IconModel(
                  icon = Icon.SmallIconWallet,
                  iconSize = IconSize.Small
                )
              ),
              title = "Restore your wallet",
              onClick = onRestoreYourWalletClick,
              trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30),
              testTag = "restore-your-wallet"
            ),
            onResetExistingDevice?.let {
              ListItemModel(
                leadingAccessory = IconAccessory(
                  iconPadding = 12,
                  model = IconModel(
                    icon = Icon.SmallIconBitkey,
                    iconSize = IconSize.Small
                  )
                ),
                title = "Wipe an existing device",
                onClick = onResetExistingDevice,
                trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30),
                testTag = "wipe-existing-device"
              )
            }
          ),
          style = ListGroupStyle.CARD_ITEM
        )
      )
    ),
    primaryButton = null
  )
