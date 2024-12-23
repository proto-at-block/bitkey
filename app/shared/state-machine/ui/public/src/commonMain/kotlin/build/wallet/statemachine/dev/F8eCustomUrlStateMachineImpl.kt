package build.wallet.statemachine.dev

import androidx.compose.runtime.*
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.debug.DebugOptionsService
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment.Custom
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.TextInput
import build.wallet.statemachine.core.form.formBodyModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Uri
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class F8eCustomUrlStateMachineImpl(
  private val debugOptionsService: DebugOptionsService,
) : F8eCustomUrlStateMachine {
  @Composable
  override fun model(props: F8eCustomUrlStateMachineProps): ScreenModel {
    val scope = rememberStableCoroutineScope()
    var customUrl by remember { mutableStateOf(props.customUrl) }
    return formBodyModel(
      id = DebugMenuEventTrackerScreenId.F8E_CUSTOM_URL_ENTRY,
      onBack = props.onBack,
      toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = props.onBack)),
      header = FormHeaderModel(headline = "Custom Fromagerie Url"),
      mainContentList = immutableListOf(
        TextInput(
          title = "Url",
          fieldModel = TextFieldModel(
            value = customUrl,
            placeholderText = "http://localhost:8080",
            onValueChange = { newValue, _ -> customUrl = newValue },
            keyboardType = Uri
          )
        )
      ),
      primaryButton = ButtonModel(
        text = "Set Url",
        size = Footer,
        onClick = StandardClick {
          scope.launch {
            debugOptionsService.setF8eEnvironment(Custom(customUrl))
            props.onBack()
          }
        }
      )
    ).asModalScreen()
  }
}
