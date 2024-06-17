package build.wallet.statemachine.account

import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun AccountAccessMoreOptionsFormBodyModel(
  onBack: () -> Unit,
  onRestoreYourWalletClick: (() -> Unit)?,
  onBeTrustedContactClick: (() -> Unit)?,
  onResetExistingDevice: (() -> Unit)?,
  onRestoreEmergencyAccessKit: (() -> Unit)?,
) = FormBodyModel(
  id = GeneralEventTrackerScreenId.ACCOUNT_ACCESS_MORE_OPTIONS,
  onBack = onBack,
  toolbar = ToolbarModel(leadingAccessory = BackAccessory(onBack)),
  header =
    FormHeaderModel(
      headline = "Welcome to Bitkey",
      subline = "How do you want to get started?"
    ),
  mainContentList =
    immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel =
          ListGroupModel(
            items =
              immutableListOfNotNull(
                onBeTrustedContactClick?.let {
                  ListItemModel(
                    leadingAccessory =
                      ListItemAccessory.IconAccessory(
                        iconPadding = 12,
                        model =
                          IconModel(
                            icon = Icon.SmallIconShieldPerson,
                            iconSize = IconSize.Small
                          )
                      ),
                    title = "Be a Trusted Contact",
                    onClick = it,
                    trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30)
                  )
                },
                onRestoreYourWalletClick?.let {
                  ListItemModel(
                    leadingAccessory =
                      ListItemAccessory.IconAccessory(
                        iconPadding = 12,
                        model =
                          IconModel(
                            icon = Icon.SmallIconWallet,
                            iconSize = IconSize.Small
                          )
                      ),
                    title = "Restore your wallet",
                    onClick = it,
                    trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30),
                    testTag = "restore-your-wallet"
                  )
                },
                onResetExistingDevice?.let {
                  ListItemModel(
                    leadingAccessory =
                      ListItemAccessory.IconAccessory(
                        iconPadding = 12,
                        model =
                          IconModel(
                            icon = Icon.SmallIconBitkey,
                            iconSize = IconSize.Small
                          )
                      ),
                    title = "Reset an existing device",
                    onClick = it,
                    trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30),
                    testTag = "reset-existing-device"
                  )
                },
                onRestoreEmergencyAccessKit?.let {
                  ListItemModel(
                    leadingAccessory =
                      ListItemAccessory.IconAccessory(
                        iconPadding = 12,
                        model =
                          IconModel(
                            icon = Icon.SmallIconRecovery,
                            iconSize = IconSize.Small
                          )
                      ),
                    title = "Import using Emergency Access Kit",
                    onClick = it,
                    trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30)
                  )
                }
              ),
            style = ListGroupStyle.CARD_ITEM
          )
      )
    ),
  primaryButton = null
)
