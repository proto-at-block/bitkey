package build.wallet.statemachine.core.input

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.TextInput
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.Tertiary
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Email
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.ButtonAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * A Form screen with an text field for email input
 *
 * @param title - The screen title
 * @param value - The current value of the email input field
 * @param primaryButton - Model of the primary continue button
 * @param onValueChange - Function handler invoked once the user changes the field value
 * @param onClose - invoked once the screen is closed
 * @param onSkip - invoked once the user chooses to skip email input
 * @param errorOverlayModel - model for the current sheet error
 */
fun EmailInputScreenModel(
  title: String,
  subline: String? = null,
  value: String = "",
  primaryButton: ButtonModel,
  onValueChange: (String) -> Unit,
  onClose: () -> Unit,
  onSkip: (() -> Unit)?,
  errorOverlayModel: SheetModel? = null,
) = ScreenModel(
  body = EmailInputScreenModel(
    title = title,
    subline = subline,
    value = value,
    primaryButton = primaryButton,
    onValueChange = onValueChange,
    onClose = onClose,
    onSkip = onSkip
  ),
  presentationStyle = ScreenPresentationStyle.Modal,
  bottomSheetModel = errorOverlayModel
)

private data class EmailInputScreenModel(
  val title: String,
  val subline: String? = null,
  val value: String = "",
  override val primaryButton: ButtonModel,
  val onValueChange: (String) -> Unit,
  val onClose: () -> Unit,
  val onSkip: (() -> Unit)?,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.EMAIL_INPUT_ENTERING_EMAIL,
    onSwipeToDismiss = onClose,
    onBack = onClose,
    toolbar =
      ToolbarModel(
        leadingAccessory = CloseAccessory(onClick = onClose),
        trailingAccessory =
          onSkip?.let {
            ButtonAccessory(
              model =
                ButtonModel(
                  text = "Skip",
                  treatment = Tertiary,
                  onClick = StandardClick(onSkip),
                  size = Compact
                )
            )
          }
      ),
    header = FormHeaderModel(headline = title, subline = subline),
    mainContentList =
      immutableListOf(
        TextInput(
          fieldModel =
            TextFieldModel(
              value = value,
              placeholderText = "Email",
              onValueChange = { newValue, _ -> onValueChange(newValue) },
              keyboardType = Email,
              onDone =
                if (primaryButton.isEnabled) {
                  { primaryButton.onClick.invoke() }
                } else {
                  null
                }
            )
        )
      ),
    primaryButton = primaryButton
  )

fun EmailTouchpointAlreadyActiveErrorSheetModel(onBack: () -> Unit) =
  SheetModel(
    body =
      ErrorFormBodyModel(
        title = "The entered email is already registered for notifications on this account.",
        subline = "Please provide a different email.",
        primaryButton =
          ButtonDataModel(
            text = "Back",
            onClick = onBack
          ),
        renderContext = Sheet,
        eventTrackerScreenId = NotificationsEventTrackerScreenId.EMAIL_ALREADY_ACTIVE_ERROR_SHEET
      ),
    onClosed = onBack
  )

fun EmailTouchpointInvalidErrorSheetModel(onBack: () -> Unit) =
  SheetModel(
    body =
      ErrorFormBodyModel(
        title = "The entered email is not valid.",
        subline = "Please provide a different email.",
        primaryButton =
          ButtonDataModel(
            text = "Back",
            onClick = onBack
          ),
        renderContext = Sheet,
        eventTrackerScreenId = NotificationsEventTrackerScreenId.EMAIL_INVALID_ERROR_SHEET
      ),
    onClosed = onBack
  )

fun EmailInputErrorSheetModel(
  isConnectivityError: Boolean,
  onBack: () -> Unit,
) = SheetModel(
  body =
    ErrorFormBodyModel(
      title = "We couldn’t add this email",
      subline =
        when {
          isConnectivityError -> "Make sure you are connected to the internet and try again."
          else -> "We are looking into this. Please try again later."
        },
      primaryButton =
        ButtonDataModel(
          text = "Back",
          onClick = onBack
        ),
      renderContext = Sheet,
      eventTrackerScreenId = NotificationsEventTrackerScreenId.EMAIL_INPUT_ERROR_SHEET
    ),
  onClosed = onBack
)
