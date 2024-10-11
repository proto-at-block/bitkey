package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.NotCompleted
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.LargeIconWarningFilled
import build.wallet.statemachine.core.Icon.SmallIconCheckFilled
import build.wallet.statemachine.core.Icon.SmallIconCircleStroked
import build.wallet.statemachine.core.Icon.SmallIconEmail
import build.wallet.statemachine.core.Icon.SmallIconMessage
import build.wallet.statemachine.core.Icon.SmallIconPushNotification
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Regular
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint.On30
import build.wallet.ui.model.icon.IconTint.Primary
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun RecoveryChannelsSetupFormBodyModel(
  pushItem: RecoveryChannelsSetupFormItemModel,
  smsItem: RecoveryChannelsSetupFormItemModel?,
  emailItem: RecoveryChannelsSetupFormItemModel,
  onBack: (() -> Unit)?,
  learnOnClick: (() -> Unit),
  continueOnClick: (() -> Unit),
  bottomSheetModel: SheetModel?,
  alertModel: ButtonAlertModel? = null,
) = ScreenModel(
  bottomSheetModel = bottomSheetModel,
  body = RecoveryChannelsSetupFormBodyModel(
    pushItem = pushItem,
    smsItem = smsItem,
    emailItem = emailItem,
    onBack = onBack,
    learnOnClick = learnOnClick,
    continueOnClick = continueOnClick
  ),
  presentationStyle = ScreenPresentationStyle.Root,
  alertModel = alertModel
)

private data class RecoveryChannelsSetupFormBodyModel(
  val pushItem: RecoveryChannelsSetupFormItemModel,
  val smsItem: RecoveryChannelsSetupFormItemModel?,
  val emailItem: RecoveryChannelsSetupFormItemModel,
  override val onBack: (() -> Unit)?,
  val learnOnClick: (() -> Unit),
  val continueOnClick: (() -> Unit),
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.NOTIFICATION_PREFERENCES_SETUP,
    onBack = onBack,
    toolbar = onBack?.let { ob ->
      ToolbarModel(leadingAccessory = BackAccessory(ob))
    },
    header =
      FormHeaderModel(
        headline = "Set up secure recovery communication channels",
        subline = "We’ll only use these channels to notify you of wallet recovery attempts and privacy updates, nothing else."
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
                secondaryText = "Learn more about wallet recovery.",
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

/**
 * App dialog informing user about push request
 */
fun RequestPushAlertModel(
  onAllow: () -> Unit,
  onDontAllow: () -> Unit,
) = ButtonAlertModel(
  title = "Recovery notifications",
  subline = "Enabling push notifications for recovery verification is highly recommended and will help keep you, and your funds, safe in case you lose your Bitkey device.",
  onDismiss = onDontAllow,
  primaryButtonText = "Allow",
  onPrimaryButtonClick = onAllow,
  secondaryButtonText = "Don't allow",
  onSecondaryButtonClick = onDontAllow,
  secondaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive
)

/**
 * App dialog directing user to open settings to modify push with the system.
 */
fun OpenSettingsForPushAlertModel(
  pushEnabled: Boolean,
  settingsOpenAction: () -> Unit,
  onClose: () -> Unit,
) = ButtonAlertModel(
  title = "Open Settings to ${
    if (pushEnabled) {
      "configure"
    } else {
      "enable"
    }
  } push notifications",
  subline = "",
  primaryButtonText = "Settings",
  secondaryButtonText = "Close",
  onDismiss = onClose,
  onPrimaryButtonClick = {
    settingsOpenAction()
  },
  onSecondaryButtonClick = onClose
)

fun ConfirmSkipRecoveryMethodsSheetModel(
  onCancel: () -> Unit,
  onContinue: () -> Unit,
) = ConfirmSkipRecoveryMethodsBodyModel(
  onCancel = onCancel,
  onContinue = onContinue
).asSheetModalScreen(onCancel)

private data class ConfirmSkipRecoveryMethodsBodyModel(
  val onCancel: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.RECOVERY_SKIP_SHEET,
    header = FormHeaderModel(
      headline = "Continue without all recovery methods?",
      subline = "To help keep your account safe and secure, we recommend enabling all " +
        "recovery methods. You can always add these later.",
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    onBack = onCancel,
    toolbar = null,
    primaryButton =
      ButtonModel(
        text = "Add recovery methods",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onCancel)
      ),
    secondaryButton =
      ButtonModel(
        text = "Skip",
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Secondary,
        onClick = StandardClick(onContinue)
      ),
    renderContext = RenderContext.Sheet
  )

fun EmailRecoveryMethodRequiredErrorModal(onCancel: () -> Unit) =
  EmailRecoveryMethodRequiredErrorBodyModal(onCancel)
    .asSheetModalScreen(onCancel)

private data class EmailRecoveryMethodRequiredErrorBodyModal(
  val onCancel: () -> Unit,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.RECOVERY_EMAIL_REQUIRED_ERROR_SHEET,
    header = FormHeaderModel(
      icon = LargeIconWarningFilled,
      headline = "An email is required",
      subline = "To keep your Bitkey safe and secure, it's important to have a way to notify " +
        "you about any security changes or recovery events when they take place.",
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    onBack = onCancel,
    toolbar = null,
    primaryButton =
      ButtonModel(
        text = "Continue",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onCancel)
      ),
    renderContext = RenderContext.Sheet
  )

private fun smsDisplayString(smsItem: RecoveryChannelsSetupFormItemModel): String {
  return if (smsItem.uiErrorHint == UiErrorHint.NotAvailableInYourCountry) {
    smsItem.uiErrorHint.displayString
  } else {
    smsItem.displayValue ?: "Recommended"
  }
}
