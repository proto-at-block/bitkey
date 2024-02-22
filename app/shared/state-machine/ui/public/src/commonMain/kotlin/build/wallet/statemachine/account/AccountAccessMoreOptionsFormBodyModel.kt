package build.wallet.statemachine.account

import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
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
import kotlinx.collections.immutable.toImmutableList

fun AccountAccessMoreOptionsFormBodyModel(
  onBack: () -> Unit,
  onRestoreYourWalletClick: () -> Unit,
  onBeTrustedContactClick: (() -> Unit)?,
  onRestoreEmergencyAccessKit: (() -> Unit)?,
) = FormBodyModel(
  id = GeneralEventTrackerScreenId.CHOOSE_ACCOUNT_ACCESS,
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
              listOfNotNull(
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
                  onClick = onRestoreYourWalletClick,
                  trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30),
                  testTag = "restore-your-wallet"
                ),
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
              ).toImmutableList(),
            style = ListGroupStyle.CARD_ITEM
          )
      )
    ),
  primaryButton = null
)
