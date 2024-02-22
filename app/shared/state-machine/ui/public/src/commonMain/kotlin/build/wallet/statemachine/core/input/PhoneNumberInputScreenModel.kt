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
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryPrimaryNoUnderline
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Phone
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.ButtonAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun PhoneNumberInputScreenModel(
  title: String,
  textFieldValue: String,
  textFieldPlaceholder: String,
  textFieldSelection: IntRange,
  onTextFieldValueChange: (String, IntRange) -> Unit,
  primaryButton: ButtonModel,
  onClose: () -> Unit,
  onSkip: (() -> Unit)?,
  errorOverlayModel: SheetModel? = null,
) = ScreenModel(
  body =
    FormBodyModel(
      id = NotificationsEventTrackerScreenId.SMS_INPUT_ENTERING_SMS,
      onBack = onClose,
      onSwipeToDismiss = onClose,
      toolbar =
        ToolbarModel(
          leadingAccessory = CloseAccessory(onClick = onClose),
          trailingAccessory =
            onSkip?.let {
              ButtonAccessory(
                model =
                  ButtonModel(
                    text = "Skip",
                    treatment = TertiaryPrimaryNoUnderline,
                    onClick = Click.standardClick { it() },
                    size = Compact
                  )
              )
            }
        ),
      header = FormHeaderModel(headline = title),
      mainContentList =
        immutableListOf(
          TextInput(
            fieldModel =
              TextFieldModel(
                value = textFieldValue,
                placeholderText = textFieldPlaceholder,
                selectionOverride = textFieldSelection,
                onValueChange = onTextFieldValueChange,
                keyboardType = Phone
              )
          )
        ),
      primaryButton = primaryButton
    ),
  presentationStyle = ScreenPresentationStyle.Modal,
  bottomSheetModel = errorOverlayModel
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
  onSkip: (() -> Unit)?,
) = ErrorFormBodyModel(
  title = "SMS notifications are not supported in your country",
  subline =
    "Bitcoin might be borderless, but our SMS notifications are still catching up. " +
      "We’re working with our partner to bring SMS notifications to your country soon.",
  primaryButton =
    when (onSkip) {
      null ->
        ButtonDataModel(
          text = "Got it",
          onClick = onBack
        )
      else ->
        ButtonDataModel(
          text = "Skip for Now",
          onClick = onSkip
        )
    },
  secondaryButton =
    when (onSkip) {
      null -> null
      else ->
        ButtonDataModel(
          text = "Use Different Country Number",
          onClick = onBack
        )
    },
  renderContext = Sheet,
  eventTrackerScreenId = NotificationsEventTrackerScreenId.SMS_UNSUPPORTED_COUNTRY_ERROR_SHEET
)
