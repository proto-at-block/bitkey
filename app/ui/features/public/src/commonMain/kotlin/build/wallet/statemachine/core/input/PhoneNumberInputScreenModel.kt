package build.wallet.statemachine.core.input

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.TextInput
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Phone
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun PhoneNumberInputScreenModel(
  title: String,
  subline: String? = null,
  sublineModel: LabelModel? = null,
  textFieldValue: String,
  textFieldPlaceholder: String,
  textFieldSelection: IntRange,
  onTextFieldValueChange: (String, IntRange) -> Unit,
  primaryButton: ButtonModel,
  secondaryButton: ButtonModel? = null,
  onClose: (() -> Unit)? = null,
  isCloseButton: Boolean = false,
  errorOverlayModel: SheetModel? = null,
) = ScreenModel(
  body = PhoneNumberInputBodyModel(
    title = title,
    subline = subline,
    sublineModel = sublineModel,
    textFieldValue = textFieldValue,
    textFieldPlaceholder = textFieldPlaceholder,
    textFieldSelection = textFieldSelection,
    onTextFieldValueChange = onTextFieldValueChange,
    primaryButton = primaryButton,
    secondaryButton = secondaryButton,
    onClose = onClose,
    isCloseButton = isCloseButton
  ),
  presentationStyle = ScreenPresentationStyle.Modal,
  bottomSheetModel = errorOverlayModel
)

data class PhoneNumberInputBodyModel(
  val title: String,
  val subline: String? = null,
  val sublineModel: LabelModel? = null,
  val textFieldValue: String,
  val textFieldPlaceholder: String,
  val textFieldSelection: IntRange,
  val onTextFieldValueChange: (String, IntRange) -> Unit,
  override val primaryButton: ButtonModel,
  override val secondaryButton: ButtonModel? = null,
  val onClose: (() -> Unit)? = null,
  val isCloseButton: Boolean = false,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.SMS_INPUT_ENTERING_SMS,
    onBack = onClose,
    onSwipeToDismiss = onClose,
    toolbar =
      ToolbarModel(
        leadingAccessory = onClose?.let {
          if (isCloseButton) CloseAccessory(onClick = it) else BackAccessory(onClick = it)
        }
      ),
    header = FormHeaderModel(
      headline = title,
      sublineModel = sublineModel ?: subline?.let { LabelModel.StringModel(it) }
    ),
    mainContentList =
      immutableListOf(
        TextInput(
          fieldModel =
            TextFieldModel(
              value = textFieldValue,
              placeholderText = textFieldPlaceholder,
              testTag = "phone-number-input-field",
              selectionOverride = textFieldSelection,
              onValueChange = onTextFieldValueChange,
              keyboardType = Phone
            )
        )
      ),
    primaryButton = primaryButton,
    secondaryButton = secondaryButton
  )

fun PhoneNumberInputErrorSheetModel(
  isConnectivityError: Boolean,
  onBack: () -> Unit,
) = ErrorFormBodyModel(
  title = "We couldn’t add this phone number",
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
  eventTrackerScreenId = NotificationsEventTrackerScreenId.SMS_INPUT_ERROR_SHEET
)

fun PhoneNumberTouchpointAlreadyActiveErrorSheetModel(onBack: () -> Unit) =
  ErrorFormBodyModel(
    title = "The entered phone number is already registered for notifications on this account.",
    subline = "Please provide a different phone number.",
    primaryButton =
      ButtonDataModel(
        text = "Back",
        onClick = onBack
      ),
    renderContext = Sheet,
    eventTrackerScreenId = NotificationsEventTrackerScreenId.SMS_ALREADY_ACTIVE_ERROR_SHEET
  )

fun PhoneNumberUnsupportedCountryErrorSheetModel(
  onBack: () -> Unit,
  primaryButtonText: String,
  primaryButtonOnClick: (() -> Unit)?,
  secondaryButtonText: String?,
  secondaryButtonOnClick: (() -> Unit)?,
) = ErrorFormBodyModel(
  title = "SMS notifications are not available in your country",
  subline = "SMS notifications are currently not supported for this country.",
  primaryButton = ButtonDataModel(
    text = primaryButtonText,
    onClick = primaryButtonOnClick ?: onBack
  ),
  secondaryButton = secondaryButtonText?.let {
    ButtonDataModel(text = it, onClick = secondaryButtonOnClick ?: onBack)
  },
  renderContext = Sheet,
  eventTrackerScreenId = NotificationsEventTrackerScreenId.SMS_UNSUPPORTED_COUNTRY_ERROR_SHEET
)
