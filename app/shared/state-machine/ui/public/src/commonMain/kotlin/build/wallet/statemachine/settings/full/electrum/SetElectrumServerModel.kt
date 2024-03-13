package build.wallet.statemachine.settings.full.electrum

import build.wallet.analytics.events.screen.id.CustomElectrumServerEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.TextInput
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Number
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Uri
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun SetElectrumServerModel(
  onClose: () -> Unit,
  host: String,
  onHostStringChanged: (String) -> Unit,
  port: String,
  onPortStringChanged: (String) -> Unit,
  setServerButtonEnabled: Boolean,
  onSetServerClick: () -> Unit,
) = FormBodyModel(
  id = CustomElectrumServerEventTrackerScreenId.CUSTOM_ELECTRUM_SERVER_UPDATE,
  onBack = onClose,
  onSwipeToDismiss = onClose,
  header =
    FormHeaderModel(
      headline = "Change Electrum Server",
      subline = "Provide details for a custom Electrum Server: "
    ),
  mainContentList =
    immutableListOf(
      TextInput(
        title = "Server:",
        fieldModel =
          TextFieldModel(
            value = host,
            placeholderText = "example.com",
            onValueChange = { newValue, _ -> onHostStringChanged(newValue) },
            keyboardType = Uri,
            focusByDefault = true
          )
      ),
      TextInput(
        title = "Port:",
        TextFieldModel(
          value = port,
          placeholderText = "50002",
          onValueChange = { newValue, _ -> onPortStringChanged(newValue) },
          keyboardType = Number,
          focusByDefault = false
        )
      )
    ),
  primaryButton =
    ButtonModel(
      text = "Save",
      isEnabled = setServerButtonEnabled,
      onClick = StandardClick(onSetServerClick),
      size = Footer
    ),
  toolbar = ToolbarModel(leadingAccessory = CloseAccessory(onClick = onClose))
)
