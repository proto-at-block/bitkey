package build.wallet.statemachine.settings.helpcenter

import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon.SmallIconArrowUpRight
import build.wallet.statemachine.core.Icon.SmallIconEmail
import build.wallet.statemachine.core.Icon.SmallIconQuestion
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_GROUP_DIVIDER
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemAccessoryAlignment.TOP
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Creates a [HelpCenterScreenModel] to display the Help Center screen
 *
 * @param onBack - Invoked once the back action is called
 */
fun HelpCenterScreenModel(
  onBack: () -> Unit,
  onFaqClick: () -> Unit,
  onContactUsClick: () -> Unit,
) = FormBodyModel(
  id = SettingsEventTrackerScreenId.SETTINGS_HELP_CENTER,
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Help Center")
    ),
  header = null,
  mainContentList =
    immutableListOf(
      ListGroup(
        listGroupModel =
          ListGroupModel(
            items =
              immutableListOf(
                ListItemModel(
                  leadingAccessory =
                    IconAccessory(
                      icon = SmallIconQuestion
                    ),
                  leadingAccessoryAlignment = TOP,
                  title = "FAQ",
                  onClick = onFaqClick,
                  trailingAccessory =
                    IconAccessory(
                      icon = SmallIconArrowUpRight
                    )
                ),
                ListItemModel(
                  leadingAccessory =
                    IconAccessory(
                      icon = SmallIconEmail
                    ),
                  leadingAccessoryAlignment = TOP,
                  title = "Contact us",
                  onClick = onContactUsClick,
                  trailingAccessory =
                    IconAccessory(
                      icon = SmallIconArrowUpRight
                    )
                )
              ),
            style = CARD_GROUP_DIVIDER
          )
      )
    ),
  primaryButton = null
)
