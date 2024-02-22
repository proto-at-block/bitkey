package build.wallet.statemachine.settings.full.notifications

import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon.SmallIconEmail
import build.wallet.statemachine.core.Icon.SmallIconMessage
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.ui.model.icon.IconTint.On30
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_GROUP_DIVIDER
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessoryAlignment.TOP
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

fun NotificationsSettingsFormBodyModel(
  smsText: String?,
  emailText: String?,
  onBack: () -> Unit,
  onSmsClick: () -> Unit,
  onEmailClick: () -> Unit,
) = FormBodyModel(
  id = SettingsEventTrackerScreenId.SETTINGS_NOTIFICATIONS,
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Notifications")
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
                    ListItemAccessory.IconAccessory(
                      icon = SmallIconMessage
                    ),
                  leadingAccessoryAlignment = TOP,
                  title = "Text Messages",
                  secondaryText = smsText,
                  onClick = onSmsClick,
                  trailingAccessory = ListItemAccessory.drillIcon(tint = On30)
                ),
                ListItemModel(
                  leadingAccessory =
                    ListItemAccessory.IconAccessory(
                      icon = SmallIconEmail
                    ),
                  leadingAccessoryAlignment = TOP,
                  title = "Emails",
                  secondaryText = emailText,
                  onClick = onEmailClick,
                  trailingAccessory = ListItemAccessory.drillIcon(tint = On30)
                )
              ),
            style = CARD_GROUP_DIVIDER
          )
      )
    ),
  primaryButton = null
)
