package build.wallet.statemachine.notifications

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer.Statement
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.notifications.NotificationPreferencesFormEditingState.*
import build.wallet.ui.compose.normalizeTestTagValue
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint.Foreground
import build.wallet.ui.model.list.*
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType

data class NotificationPreferenceFormBodyModel(
  val transactionPush: Boolean,
  val updatesPush: Boolean,
  val updatesEmail: Boolean,
  val onTransactionPushToggle: (Boolean) -> Unit,
  val onUpdatesPushToggle: (Boolean) -> Unit,
  val onUpdatesEmailToggle: (Boolean) -> Unit,
  val formEditingState: NotificationPreferencesFormEditingState,
  override val onBack: () -> Unit,
  val continueOnClick: (() -> Unit),
  val onMoneyMovementLearnMore: () -> Unit,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.NOTIFICATION_PREFERENCES_SELECTION,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onBack)),
    header =
      FormHeaderModel(
        headline = "Notifications and updates",
      ),
    mainContentList =
      immutableListOfNotNull(
        Explainer(
          immutableListOf(
            Statement(
              title = "Transactions",
              titleLabelType = LabelType.Body1Bold,
              body = LabelModel.LinkSubstringModel.from(
                substringToOnClick = mapOf(
                  "Learn more" to onMoneyMovementLearnMore
                ),
                string = "Get notified when you receive bitcoin. Wallet addresses are stored on Bitkey servers only while notifications are on. Learn more",
                underline = true,
                bold = true,
                color = LabelModel.Color.FOREGROUND
              )
            )
          )
        ),
        ListGroup(
          listGroupModel =
            ListGroupModel(
              items =
                immutableListOf(
                  createListItem(
                    title = "Push",
                    icon = DotNotifyPush,
                    checked = transactionPush,
                    onCheckedChanged = onTransactionPushToggle,
                    enabled = formEditingState != Loading,
                    interactionsEnabled = formEditingState == Editing
                  )
                ),
              style = ListGroupStyle.DIVIDER
            )
        ),
        Explainer(
          immutableListOf(
            Statement(
              title = "Bitkey updates",
              titleLabelType = LabelType.Body1Bold,
              body = "Learn about new Bitkey features and easily send us customer feedback."
            )
          )
        ),
        ListGroup(
          listGroupModel =
            ListGroupModel(
              items =
                immutableListOf(
                  createListItem(
                    title = "Push",
                    icon = DotNotifyPush,
                    checked = updatesPush,
                    onCheckedChanged = onUpdatesPushToggle,
                    enabled = formEditingState != Loading,
                    interactionsEnabled = formEditingState == Editing
                  ),
                  createListItem(
                    title = "Email",
                    icon = DotNotifyEmail,
                    checked = updatesEmail,
                    onCheckedChanged = onUpdatesEmailToggle,
                    enabled = formEditingState != Loading,
                    interactionsEnabled = formEditingState == Editing
                  )
                ),
              style = ListGroupStyle.DIVIDER
            )
        )
      ),
    primaryButton = ButtonModel(
      text = "Continue",
      isLoading = formEditingState == Loading || formEditingState == Submitting,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(continueOnClick)
    )
  )

enum class NotificationPreferencesFormEditingState {
  Loading,
  Overlay,
  Submitting,
  Editing,
}

private fun createListItem(
  title: String,
  icon: Icon? = null,
  checked: Boolean,
  onCheckedChanged: (Boolean) -> Unit,
  enabled: Boolean,
  interactionsEnabled: Boolean = enabled,
): ListItemModel {
  val titleTag = normalizeTestTagValue(title, fallback = "notification")
  return ListItemModel(
    leadingAccessory = icon?.run {
      IconAccessory(
        model =
          IconModel(
            icon = this,
            iconTint = Foreground,
            iconSize = IconSize.Regular
          )
      )
    },
    title = title,
    treatment = ListItemTreatment.PRIMARY,
    trailingAccessory = ListItemAccessory.SwitchAccessory(
      model =
        SwitchModel(
          checked = checked,
          testTag = "notifications-preference-$titleTag-toggle",
          onCheckedChange = onCheckedChanged,
          enabled = enabled,
          interactionsEnabled = interactionsEnabled
        )
    ),
    onClick = null
  )
}

data class NetworkingErrorSheetBodyModel(
  val onClose: () -> Unit,
  val networkingError: Error,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.RECOVERY_CHANNELS_SETTINGS_NETWORKING_ERROR_SHEET,
    header = FormHeaderModel(
      icon = LargeIconNetworkError,
      headline = "A networking error has occurred. Please try again.",
      subline = networkingError.message,
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    onBack = onClose,
    toolbar = null,
    primaryButton =
      ButtonModel(
        text = "Close",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onClose)
      )
  )
