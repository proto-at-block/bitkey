package build.wallet.statemachine.recovery.emergencyexitkit

import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class EmergencyExitKitImportPasteAppKeyBodyModel(
  val enteredText: String,
  override val onBack: () -> Unit,
  val onEnterTextChanged: (String) -> Unit,
  val onPasteButtonClick: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    id = EmergencyAccessKitTrackerScreenId.IMPORT_TEXT_KEY,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(
        onClick = onBack
      )
    ),
    header = FormHeaderModel(
      headline = "Enter your Emergency Exit Kit details",
      sublineModel =
        LabelModel.StringWithStyledSubstringModel.from(
          string = "Copy the code from the box labeled App Key backup directly from your Emergency Exit Kit, and paste it into the field below",
          boldedSubstrings = immutableListOf("App Key backup")
        )
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.AddressInput(
        fieldModel = TextFieldModel(
          value = enteredText,
          placeholderText = "App Key code",
          onValueChange = { newText, _ -> onEnterTextChanged(newText) },
          keyboardType = TextFieldModel.KeyboardType.Default
        ),
        trailingButtonModel = if (enteredText.isEmpty()) {
          ButtonModel(
            text = "Paste",
            leadingIcon = Icon.SmallIconClipboard,
            treatment = ButtonModel.Treatment.Secondary,
            size = ButtonModel.Size.Compact,
            onClick = StandardClick(onPasteButtonClick)
          )
        } else {
          null
        }
      )
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      isEnabled = enteredText.isNotEmpty(),
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onContinue)
    )
  )
