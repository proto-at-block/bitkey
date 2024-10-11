package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.NeedsAction
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.Skipped
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconCheckFilled
import build.wallet.statemachine.core.Icon.SmallIconEmail
import build.wallet.statemachine.core.Icon.SmallIconMessage
import build.wallet.statemachine.core.Icon.SmallIconPushNotification
import build.wallet.statemachine.core.Icon.SmallIconXFilled
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.icon.IconTint.On30
import build.wallet.ui.model.icon.IconTint.On60
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_GROUP_DIVIDER
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment

fun NotificationPreferencesSetupFormBodyModel(
  pushItem: NotificationPreferencesSetupFormItemModel,
  smsItem: NotificationPreferencesSetupFormItemModel,
  emailItem: NotificationPreferencesSetupFormItemModel,
  alertModel: ButtonAlertModel? = null,
) = ScreenModel(
  body = NotificationPreferencesSetupBodyModel(
    pushItem = pushItem,
    smsItem = smsItem,
    emailItem = emailItem
  ),
  presentationStyle = ScreenPresentationStyle.Root,
  alertModel = alertModel
)

private data class NotificationPreferencesSetupBodyModel(
  val pushItem: NotificationPreferencesSetupFormItemModel,
  val smsItem: NotificationPreferencesSetupFormItemModel,
  val emailItem: NotificationPreferencesSetupFormItemModel,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.NOTIFICATION_PREFERENCES_SETUP,
    onBack = null,
    toolbar = null,
    header =
      FormHeaderModel(
        headline = "Set up notifications to keep your wallet secure",
        subline = "Stay informed with security alerts and verify your recovery attempts. "
      ),
    mainContentList =
      immutableListOf(
        ListGroup(
          listGroupModel =
            ListGroupModel(
              items =
                immutableListOf(
                  with(pushItem) {
                    ListItemModel(
                      leadingAccessory =
                        IconAccessory(
                          model =
                            IconModel(
                              iconImage = LocalImage(state.icon(SmallIconPushNotification)),
                              iconTint = state.iconTint(),
                              iconSize = build.wallet.ui.model.icon.IconSize.Small
                            )
                        ),
                      title = "Push Notifications",
                      treatment = state.treatment(),
                      trailingAccessory = state.trailingAccessory(),
                      onClick = onClick
                    )
                  },
                  with(smsItem) {
                    ListItemModel(
                      leadingAccessory =
                        IconAccessory(
                          model =
                            IconModel(
                              icon = state.icon(SmallIconMessage),
                              iconTint = state.iconTint(),
                              iconSize = build.wallet.ui.model.icon.IconSize.Small
                            )
                        ),
                      title = "Text Messages",
                      treatment = state.treatment(),
                      trailingAccessory = state.trailingAccessory(),
                      onClick = onClick
                    )
                  },
                  with(emailItem) {
                    ListItemModel(
                      leadingAccessory =
                        IconAccessory(
                          model =
                            IconModel(
                              iconImage = LocalImage(state.icon(SmallIconEmail)),
                              iconTint = state.iconTint(),
                              iconSize = build.wallet.ui.model.icon.IconSize.Small
                            )
                        ),
                      title = "Emails",
                      treatment = state.treatment(),
                      trailingAccessory = state.trailingAccessory(),
                      onClick = onClick
                    )
                  }
                ),
              style = CARD_GROUP_DIVIDER
            )
        )
      ),
    // No primary button, instead the screen auto-advances when all channels are either
    // completed or skipped
    primaryButton = null
  )

private fun NotificationPreferencesSetupFormItemModel.State.icon(needsActionIcon: Icon) =
  when (this) {
    NeedsAction -> needsActionIcon
    Completed -> SmallIconCheckFilled
    Skipped -> SmallIconXFilled
  }

private fun NotificationPreferencesSetupFormItemModel.State.treatment(): ListItemTreatment =
  when (this) {
    NeedsAction -> ListItemTreatment.PRIMARY
    Completed, Skipped -> ListItemTreatment.SECONDARY
  }

private fun NotificationPreferencesSetupFormItemModel.State.trailingAccessory(): ListItemAccessory? =
  when (this) {
    NeedsAction -> ListItemAccessory.drillIcon(tint = On30)
    Completed, Skipped -> null
  }

private fun NotificationPreferencesSetupFormItemModel.State.iconTint(): IconTint? =
  when (this) {
    NeedsAction -> null
    Completed, Skipped -> On60
  }
