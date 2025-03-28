package bitkey.ui.screens.demo

import androidx.compose.runtime.*
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.compose.collections.immutableListOf
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.TextInput
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data object DemoModeCodeEntryScreen : Screen

@BitkeyInject(ActivityScope::class)
class DemoModeCodeEntryScreenPresenter : ScreenPresenter<DemoModeCodeEntryScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: DemoModeCodeEntryScreen,
  ): ScreenModel {
    var demoModeCode by remember { mutableStateOf("") }

    return DemoCodeEntryIdleBodyModel(
      onValueChange = {
        demoModeCode = it
      },
      onBack = {
        navigator.goTo(DemoModeDisabledScreen)
      },
      onSubmit = {
        navigator.goTo(DemoCodeEntrySubmissionScreen(demoModeCode))
      }
    ).asRootFullScreen()
  }
}

private data class DemoCodeEntryIdleBodyModel(
  override val onBack: () -> Unit,
  val onSubmit: () -> Unit,
  val onValueChange: (String) -> Unit,
) : FormBodyModel(
    id = DemoCodeTrackerScreenId.DEMO_MODE_CODE_ENTRY,
    onBack = onBack,
    onSwipeToDismiss = onBack,
    header = FormHeaderModel(headline = "Enter demo mode code"),
    mainContentList = immutableListOf(
      TextInput(
        fieldModel = TextFieldModel(
          value = "",
          placeholderText = "",
          onValueChange = { newValue, _ -> onValueChange(newValue) },
          keyboardType = TextFieldModel.KeyboardType.Uri
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Submit",
      isEnabled = true,
      onClick = StandardClick(onSubmit),
      size = ButtonModel.Size.Footer
    ),
    toolbar = ToolbarModel(leadingAccessory = CloseAccessory(onBack))
  )
