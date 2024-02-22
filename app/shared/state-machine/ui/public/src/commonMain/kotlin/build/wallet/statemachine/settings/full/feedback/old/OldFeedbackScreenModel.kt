package build.wallet.statemachine.settings.full.feedback.old

import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon.SmallIconArrowUpRight
import build.wallet.statemachine.core.Icon.SmallIconEmail
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_GROUP
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemAccessoryAlignment.TOP
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Creates a [OldFeedbackScreenModel] to display the Feedback screen
 *
 * @param onBack - Invoked once the back action is called
 * @param onContactUsClick - Invoked once the contact us link is clicked
 */
fun OldFeedbackScreenModel(
  onBack: () -> Unit,
  onContactUsClick: () -> Unit,
) = FormBodyModel(
  id = SettingsEventTrackerScreenId.SETTINGS_SEND_FEEDBACK,
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Send feedback")
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
            style = CARD_GROUP
          )
      )
    ),
  primaryButton = null
)
