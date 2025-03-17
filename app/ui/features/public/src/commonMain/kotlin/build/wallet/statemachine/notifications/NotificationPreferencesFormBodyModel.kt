package build.wallet.statemachine.notifications

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.ktor.result.NetworkingError
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.Icon.SmallIconEmail
import build.wallet.statemachine.core.Icon.SmallIconPushNotification
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer.Statement
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.notifications.NotificationPreferencesFormEditingState.Editing
import build.wallet.statemachine.notifications.NotificationPreferencesFormEditingState.Loading
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint.On30
import build.wallet.ui.model.icon.IconTint.Primary
import build.wallet.ui.model.label.CallToActionModel
import build.wallet.ui.model.list.*
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class TosInfo(
  val termsAgree: Boolean,
  val onTermsAgreeToggle: (Boolean) -> Unit,
  val tosLink: () -> Unit,
  val privacyLink: () -> Unit,
)

data class NotificationPreferenceFormBodyModel(
  val transactionPush: Boolean,
  val updatesPush: Boolean,
  val updatesEmail: Boolean,
  val tosInfo: TosInfo?,
  val onTransactionPushToggle: (Boolean) -> Unit,
  val onUpdatesPushToggle: (Boolean) -> Unit,
  val onUpdatesEmailToggle: (Boolean) -> Unit,
  val formEditingState: NotificationPreferencesFormEditingState,
  val ctaModel: CallToActionModel?,
  override val onBack: () -> Unit,
  val continueOnClick: (() -> Unit),
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.NOTIFICATION_PREFERENCES_SELECTION,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onBack)),
    header =
      FormHeaderModel(
        headline = "Notifications and updates",
        subline = "Customize the notifications you receive for transactions and product updates."
      ),
    mainContentList =
      immutableListOfNotNull(
        Explainer(
          immutableListOf(
            Statement(
              title = "Transactions",
              body = "Get notified when you receive sats."
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
                    icon = SmallIconPushNotification,
                    checked = transactionPush,
                    onCheckedChanged = onTransactionPushToggle,
                    enabled = formEditingState == Editing
                  )
                ),
              style = ListGroupStyle.DIVIDER
            )
        ),
        Explainer(
          immutableListOf(
            Statement(
              title = "Bitkey updates",
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
                    icon = SmallIconPushNotification,
                    checked = updatesPush,
                    onCheckedChanged = onUpdatesPushToggle,
                    enabled = formEditingState == Editing
                  ),
                  createListItem(
                    title = "Email",
                    icon = SmallIconEmail,
                    checked = updatesEmail,
                    onCheckedChanged = onUpdatesEmailToggle,
                    enabled = formEditingState == Editing
                  )
                ),
              style = ListGroupStyle.DIVIDER
            )
        ),
        tosInfo?.let { ti ->
          ListGroup(
            listGroupModel =
              ListGroupModel(
                items =
                  immutableListOf(
                    ListItemModel(
                      leadingAccessory = null,
                      title = "TOS",
                      titleLabel = LabelModel.LinkSubstringModel.from(
                        substringToOnClick = mapOf(
                          Pair(
                            first = "Terms of Service",
                            second = {
                              if (formEditingState == Editing) {
                                ti.tosLink()
                              }
                            }
                          ),
                          Pair(
                            first = "Privacy Notice",
                            second = {
                              if (formEditingState == Editing) {
                                ti.privacyLink()
                              }
                            }
                          )
                        ),
                        string = "I agree to Bitkey’s Terms of Service and Privacy Notice",
                        underline = false,
                        bold = false
                      ),
                      treatment = ListItemTreatment.PRIMARY,
                      trailingAccessory = IconAccessory(
                        onClick = { ti.onTermsAgreeToggle(!ti.termsAgree) },
                        model = IconModel(
                          icon = if (ti.termsAgree) {
                            Icon.SmallIconCheckFilled
                          } else {
                            Icon.SmallIconCircleStroked
                          },
                          iconSize = IconSize.Regular,
                          iconTint = if (ti.termsAgree) {
                            Primary
                          } else {
                            On30
                          }
                        )
                      )
                    )
                  ),
                style = ListGroupStyle.DIVIDER
              )
          )
        }
      ),
    ctaWarning = ctaModel,
    primaryButton = ButtonModel(
      text = "Continue",
      isLoading = formEditingState == Loading,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(continueOnClick)
    )
  )

enum class NotificationPreferencesFormEditingState {
  Loading,
  Overlay,
  Editing,
}

private fun createListItem(
  title: String,
  icon: Icon? = null,
  checked: Boolean,
  onCheckedChanged: (Boolean) -> Unit,
  enabled: Boolean,
): ListItemModel {
  return ListItemModel(
    leadingAccessory = icon?.run {
      IconAccessory(
        model =
          IconModel(
            icon = this,
            iconTint = On30,
            iconSize = Small
          )
      )
    },
    title = title,
    treatment = ListItemTreatment.PRIMARY,
    trailingAccessory = ListItemAccessory.SwitchAccessory(
      model =
        SwitchModel(
          checked = checked,
          onCheckedChange = onCheckedChanged,
          enabled = enabled
        )
    ),
    onClick = null
  )
}

data class NetworkingErrorState(val networkingError: NetworkingError, val onClose: () -> Unit)

/**
 * Show user a basic error message when there's a networking issue.
 * Closing the error will do different things depending on state:
 *
 * On loading, we go back to settings. Onboarding doesn't load.
 * On saving, the screen will revert to editing and the user can try again.
 */
fun NetworkingErrorSheetModel(
  onClose: () -> Unit,
  networkingError: NetworkingError,
) = SheetModel(
  size = SheetSize.MIN40,
  body = NetworkingErrorSheetBodyModel(
    onClose = onClose,
    networkingError = networkingError
  ),
  onClosed = onClose
)

private data class NetworkingErrorSheetBodyModel(
  val onClose: () -> Unit,
  val networkingError: NetworkingError,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.RECOVERY_CHANNELS_SETTINGS_NETWORKING_ERROR_SHEET,
    header = FormHeaderModel(
      icon = Icon.LargeIconNetworkError,
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
