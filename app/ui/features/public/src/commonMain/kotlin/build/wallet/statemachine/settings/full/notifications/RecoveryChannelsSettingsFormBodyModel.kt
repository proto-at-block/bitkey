package build.wallet.statemachine.settings.full.notifications

import bitkey.notifications.NotificationChannel
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.ktor.result.NetworkingError
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Avatar
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint.On30
import build.wallet.ui.model.list.*
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun RecoveryChannelsSettingsFormBodyModel(
  source: Source,
  missingRecoveryMethods: List<NotificationChannel>,
  pushItem: RecoveryChannelsSettingsFormItemModel,
  smsItem: RecoveryChannelsSettingsFormItemModel,
  emailItem: RecoveryChannelsSettingsFormItemModel,
  onBack: (() -> Unit)?,
  learnOnClick: (() -> Unit),
  continueOnClick: (() -> Unit)?,
  bottomSheetModel: SheetModel?,
  alertModel: ButtonAlertModel? = null,
) = ScreenModel(
  bottomSheetModel = bottomSheetModel,
  body = RecoveryChannelsSettingsFormBodyModel(
    source = source,
    missingRecoveryMethods = missingRecoveryMethods,
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

data class RecoveryChannelsSettingsFormBodyModel(
  val source: Source,
  val missingRecoveryMethods: List<NotificationChannel>,
  val pushItem: RecoveryChannelsSettingsFormItemModel,
  val smsItem: RecoveryChannelsSettingsFormItemModel,
  val emailItem: RecoveryChannelsSettingsFormItemModel,
  override val onBack: (() -> Unit)?,
  val learnOnClick: (() -> Unit),
  val continueOnClick: (() -> Unit)?,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.RECOVERY_CHANNELS_SETTINGS,
    onBack = onBack,
    toolbar = onBack?.let { ob ->
      ToolbarModel(leadingAccessory = BackAccessory(ob))
    },
    header =
      FormHeaderModel(
        headline = when (source) {
          Source.Settings -> "Critical Alerts"
          Source.Onboarding, Source.InheritanceStartClaim -> "Enable critical alerts"
        },
        subline = when (source) {
          Source.Settings -> "This is how we communicate critical recovery, inheritance, and privacy updates."
          Source.Onboarding, Source.InheritanceStartClaim -> "You will only receive alerts about recovery attempts, inheritance, and privacy updates."
        }
      ),
    mainContentList =
      immutableListOfNotNull(
        ListGroup(
          listGroupModel = ListGroupModel(
            style = ListGroupStyle.CARD_GROUP,
            items = immutableListOf(
              ListItemModel(
                title = "Missing ${
                  when (source) {
                    Source.Onboarding, Source.Settings -> "recovery"
                    Source.InheritanceStartClaim -> "alert"
                  }
                } ${
                  if (missingRecoveryMethods.size > 1) {
                    "methods"
                  } else {
                    "method"
                  }
                }",
                secondaryText = missingRecoveryModelDescription(missingRecoveryMethods)
              )
            )
          )
        ).takeIf { missingRecoveryMethods.isNotEmpty() },
        ListGroup(
          listGroupModel =
            ListGroupModel(
              items =
                immutableListOf(
                  createListItem(
                    itemModel = emailItem,
                    icon = SmallIconEmail,
                    title = "Email",
                    secondaryText = emailItem.displayValue ?: "Required"
                  ),
                  createListItem(
                    itemModel = smsItem,
                    icon = SmallIconMessage,
                    title = "SMS",
                    secondaryText = when (smsItem.enabled) {
                      EnabledState.Loading -> ""
                      EnabledState.Enabled -> smsItem.displayValue ?: "Recommended"
                      EnabledState.Disabled -> smsItem.uiErrorHint?.displayString ?: "Recommended"
                    }
                  ),
                  createListItem(
                    itemModel = pushItem,
                    icon = SmallIconPushNotification,
                    title = "Push notifications",
                    secondaryText = when (pushItem.enabled) {
                      EnabledState.Loading -> ""
                      EnabledState.Enabled -> "Enabled"
                      EnabledState.Disabled -> "Recommended"
                    }
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
        ).takeIf { source != Source.InheritanceStartClaim }
      ),
    primaryButton = ButtonModel(
      text = "Continue",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick {
        continueOnClick?.invoke()
      }
    ).takeIf { continueOnClick != null },
    secondaryButton = ButtonModel(
      text = "Learn more",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick {
        learnOnClick()
      }
    ).takeIf { source == Source.InheritanceStartClaim }
  )

private fun createListItem(
  itemModel: RecoveryChannelsSettingsFormItemModel,
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
    trailingAccessory = IconAccessory(SmallIconCaretRight),
    onClick = onClick
  )
}

enum class Source {
  Onboarding,
  Settings,
  InheritanceStartClaim,
}

private fun createSheetFormHeader(
  icon: Icon,
  headline: String,
  subline: String? = null,
): FormHeaderModel =
  FormHeaderModel(
    iconModel = IconModel(
      icon = icon,
      iconSize = Avatar,
      iconTint = On30
    ),
    headline = headline,
    sublineModel = LabelModel.StringWithStyledSubstringModel.from(
      string = subline ?: "",
      substringToColor = emptyMap()
    ).takeIf { subline != null },
    alignment = FormHeaderModel.Alignment.LEADING
  )

fun NetworkingErrorSheetModel(
  onClose: () -> Unit,
  networkingError: NetworkingError,
) = NetworkingErrorBodyModel(
  onClose = onClose,
  networkingError = networkingError
).asSheetModalScreen(onClose)

private data class NetworkingErrorBodyModel(
  val onClose: () -> Unit,
  val networkingError: NetworkingError,
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
      ),
    renderContext = RenderContext.Sheet
  )

fun PushToggleSheetModel(
  source: Source,
  onCancel: () -> Unit,
  onToggle: () -> Unit,
  isEnabled: Boolean,
) = PushToggleFormBodyModel(
  source = source,
  onCancel = onCancel,
  onToggle = onToggle,
  isEnabled = isEnabled
).asSheetModalScreen(onCancel)

private data class PushToggleFormBodyModel(
  val source: Source,
  val onCancel: () -> Unit,
  val onToggle: () -> Unit,
  val isEnabled: Boolean,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.RECOVERY_CHANNELS_SETTINGS_PUSH_TOGGLE_SHEET,
    header = createSheetFormHeader(
      icon = SmallIconPushNotification,
      headline = "Edit push notifications ${if (source == Source.InheritanceStartClaim) "alerts" else "recovery"}"
    ),
    onBack = onCancel,
    toolbar = null,
    primaryButton =
      ButtonModel(
        text = "${
          if (isEnabled) {
            "Disable"
          } else {
            "Enable"
          }
        } push notifications",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onToggle),
        treatment = if (isEnabled) {
          ButtonModel.Treatment.SecondaryDestructive
        } else {
          ButtonModel.Treatment.Primary
        }
      ),
    secondaryButton =
      ButtonModel(
        text = "Cancel edits",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onCancel),
        treatment = if (isEnabled) {
          ButtonModel.Treatment.Primary
        } else {
          ButtonModel.Treatment.Secondary
        }
      ),
    renderContext = RenderContext.Sheet
  )

fun SMSEditSheetModel(
  source: Source,
  onCancel: () -> Unit,
  onEnableDisable: () -> Unit,
  onEditNumber: () -> Unit,
  enableNumber: String?,
) = SMSEditFormBodyModel(
  source = source,
  onCancel = onCancel,
  onEnableDisable = onEnableDisable,
  onEditNumber = onEditNumber,
  enableNumber = enableNumber
).asSheetModalScreen(onCancel)

private data class SMSEditFormBodyModel(
  val source: Source,
  val onCancel: () -> Unit,
  val onEnableDisable: () -> Unit,
  val onEditNumber: () -> Unit,
  val enableNumber: String?,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.RECOVERY_CHANNELS_SETTINGS_SMS_EDIT_SHEET,
    header = createSheetFormHeader(
      icon = SmallIconMessage,
      headline = "Edit SMS ${if (source == Source.InheritanceStartClaim) "alerts" else "recovery"}",
      subline = "You can use a different phone number or disable it entirely."
    ),
    onBack = onCancel,
    toolbar = null,
    primaryButton =
      ButtonModel(
        text = "Edit number",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onEditNumber)
      ),
    secondaryButton = if (enableNumber == null) {
      ButtonModel(
        text = "Disable SMS ${if (source == Source.InheritanceStartClaim) "alerts" else "recovery"}",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onEnableDisable),
        treatment = ButtonModel.Treatment.SecondaryDestructive
      )
    } else {
      ButtonModel(
        text = "Enable SMS ${if (source == Source.InheritanceStartClaim) "alerts" else "recovery"} for $enableNumber",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onEnableDisable),
        treatment = ButtonModel.Treatment.Secondary
      )
    },
    tertiaryButton = ButtonModel(
      text = "Cancel edits",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onCancel),
      treatment = ButtonModel.Treatment.Secondary
    ),
    renderContext = RenderContext.Sheet
  )

fun SMSNonUSSheetModel(
  source: Source,
  onCancel: () -> Unit,
  onContinue: () -> Unit,
) = SMSNonUSBodyModel(
  source = source,
  onCancel = onCancel,
  onContinue = onContinue
).asSheetModalScreen(onCancel)

private data class SMSNonUSBodyModel(
  val source: Source,
  val onCancel: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.RECOVERY_CHANNELS_SETTINGS_SMS_NON_US_SHEET,
    header = createSheetFormHeader(
      icon = SmallIconMessage,
      headline = "SMS updates are not available with US numbers",
      subline = "If you’d like to add SMS as ${if (source == Source.InheritanceStartClaim) "an alert" else "a recovery"} method, you’ll need to use a non-US phone number."
    ),
    onBack = onCancel,
    toolbar = null,
    primaryButton =
      ButtonModel(
        text = "Add a non-US number",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onContinue)
      ),
    secondaryButton =
      ButtonModel(
        text = "Cancel",
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Secondary,
        onClick = StandardClick(onCancel)
      ),
    renderContext = RenderContext.Sheet
  )

private fun missingRecoveryModelDescription(
  missingRecoveryMethods: List<NotificationChannel>,
): String? {
  return if (missingRecoveryMethods.isEmpty()) {
    null
  } else {
    val missingMethodsNames = listOfNotNull(
      "SMS".takeIf { missingRecoveryMethods.contains(NotificationChannel.Sms) },
      "push notifications".takeIf { missingRecoveryMethods.contains(NotificationChannel.Push) }
    )

    "Enable ${missingMethodsNames.joinToString(separator = " and ")} for critical alerts."
  }
}
