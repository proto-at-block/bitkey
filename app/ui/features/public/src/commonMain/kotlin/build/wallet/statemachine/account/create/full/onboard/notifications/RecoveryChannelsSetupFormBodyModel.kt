package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.NotCompleted
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Regular
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint.On30
import build.wallet.ui.model.icon.IconTint.Primary
import build.wallet.ui.model.list.*
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class RecoveryChannelsSetupFormBodyModel(
  val pushItem: RecoveryChannelsSetupFormItemModel,
  val smsItem: RecoveryChannelsSetupFormItemModel?,
  val emailItem: RecoveryChannelsSetupFormItemModel,
  val learnOnClick: (() -> Unit),
  val continueOnClick: (() -> Unit),
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.NOTIFICATION_PREFERENCES_SETUP,
    onBack = null, // Do not allow users to go back to cloud backup step
    toolbar = ToolbarModel(),
    header = FormHeaderModel(
      headline = "Set up critical alerts",
      subline = "You will only receive alerts about recovery attempts, inheritance, and privacy updates."
    ),
    mainContentList =
      immutableListOf(
        ListGroup(
          listGroupModel =
            ListGroupModel(
              items = immutableListOfNotNull(
                createListItem(
                  itemModel = emailItem,
                  icon = SmallIconEmail,
                  title = "Email",
                  secondaryText = emailItem.displayValue ?: "Required"
                ),
                smsItem?.run {
                  createListItem(
                    itemModel = this,
                    icon = SmallIconMessage,
                    title = "SMS",
                    secondaryText = smsDisplayString(this)
                  )
                },
                createListItem(
                  itemModel = pushItem,
                  icon = SmallIconPushNotification,
                  title = "Push notifications",
                  secondaryText = "Recommended"
                )
              ),
              style = ListGroupStyle.DIVIDER
            )
        ),
        ListGroup(
          listGroupModel = ListGroupModel(
            style = ListGroupStyle.CARD_GROUP,
            items = immutableListOf(
              ListItemModel(
                title = "We’re serious about security",
                secondaryText = "Learn more about critical alerts for recovery and inheritance.",
                trailingAccessory = ListItemAccessory.drillIcon(),
                onClick = learnOnClick
              )
            )
          )
        )
      ),
    primaryButton = ButtonModel(
      text = "Continue",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(continueOnClick)
    )
  )

private fun createListItem(
  itemModel: RecoveryChannelsSetupFormItemModel,
  icon: Icon,
  title: String,
  secondaryText: String,
) = with(itemModel) {
  ListItemModel(
    leadingAccessory =
      IconAccessory(
        model =
          IconModel(
            icon = icon,
            iconTint = On30,
            iconSize = Small
          )
      ),
    title = title,
    secondaryText = secondaryText,
    treatment = ListItemTreatment.PRIMARY,
    trailingAccessory = state.trailingAccessory(),
    onClick = onClick
  )
}

private fun RecoveryChannelsSetupFormItemModel.State.trailingAccessory(): ListItemAccessory? =
  when (this) {
    NotCompleted -> IconAccessory(
      model = IconModel(
        icon = SmallIconCircleStroked,
        iconSize = Regular,
        iconTint = On30
      )
    )

    Completed -> IconAccessory(
      model = IconModel(
        icon = SmallIconCheckFilled,
        iconSize = Regular,
        iconTint = Primary
      )
    )
  }

private fun smsDisplayString(smsItem: RecoveryChannelsSetupFormItemModel): String {
  return if (smsItem.uiErrorHint == UiErrorHint.NotAvailableInYourCountry) {
    smsItem.uiErrorHint.displayString
  } else {
    smsItem.displayValue ?: "Recommended"
  }
}
