package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.F8eEnvironment.Custom
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.TextInput
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Uri
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

class F8eCustomUrlStateMachineImpl : F8eCustomUrlStateMachine {
  @Composable
  override fun model(props: F8eCustomUrlStateMachineProps): ScreenModel {
    var customUrl by remember { mutableStateOf(props.customUrl) }
    return FormBodyModel(
      id = DebugMenuEventTrackerScreenId.F8E_CUSTOM_URL_ENTRY,
      onBack = props.onBack,
      toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = props.onBack)),
      header = FormHeaderModel(headline = "Custom Fromagerie Url"),
      mainContentList =
        immutableListOf(
          TextInput(
            title = "Url",
            fieldModel =
              TextFieldModel(
                value = customUrl,
                placeholderText = "http://localhost:8080",
                onValueChange = { newValue, _ -> customUrl = newValue },
                keyboardType = Uri
              )
          )
        ),
      primaryButton =
        ButtonModel(
          text = "Set Url",
          size = Footer,
          onClick =
            Click.standardClick {
              props.templateKeyboxConfigData.updateConfig {
                it.copy(
                  f8eEnvironment = Custom(customUrl)
                )
              }
              props.onBack()
            }
        )
    ).asModalScreen()
  }
}
