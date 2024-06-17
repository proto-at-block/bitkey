package build.wallet.statemachine.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.demo.DemoModeF8eClient
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.TextInput
import build.wallet.time.Delayer
import build.wallet.time.withMinimumDelay
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.mapBoth

class DemoModeCodeEntryUiStateMachineImpl(
  private val demoModeF8eClient: DemoModeF8eClient,
  private val delayer: Delayer,
) : DemoModeCodeEntryUiStateMachine {
  @Composable
  override fun model(props: DemoCodeEntryUiProps): ScreenModel {
    var uiState: DemoModeState by remember { mutableStateOf(DemoModeState.DemoCodeEntryIdleState) }
    var demoModeCode by remember { mutableStateOf("") }

    return when (uiState) {
      is DemoModeState.DemoCodeEntryIdleState ->
        FormBodyModel(
          id = DemoCodeTrackerScreenId.DEMO_MODE_CODE_ENTRY,
          onBack = props.onBack,
          onSwipeToDismiss = props.onBack,
          header =
            FormHeaderModel(
              headline = "Enter demo mode code"
            ),
          mainContentList =
            immutableListOf(
              TextInput(
                fieldModel =
                  TextFieldModel(
                    value = "",
                    placeholderText = "",
                    onValueChange = { newValue, _ -> demoModeCode = newValue },
                    keyboardType = TextFieldModel.KeyboardType.Uri
                  )
              )
            ),
          primaryButton =
            ButtonModel(
              text = "Submit",
              isEnabled = true,
              onClick = StandardClick { uiState = DemoModeState.DemoCodeEntrySubmissionState },
              size = ButtonModel.Size.Footer
            ),
          toolbar = ToolbarModel(
            leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(
              onClick = props.onBack
            )
          )
        ).asModalScreen()

      is DemoModeState.DemoCodeEntrySubmissionState -> {
        LaunchedEffect("submit-demo-code") {
          delayer.withMinimumDelay {
            demoModeF8eClient.initiateDemoMode(
              f8eEnvironment = props.accountData.templateFullAccountConfigData.config.f8eEnvironment,
              code = demoModeCode
            )
          }.mapBoth(
            success = {
              uiState = DemoModeState.DemoCodeEntryIdleState
            },
            failure = {
              uiState = DemoModeState.DemoCodeEntryIdleState
              props.onCodeSuccess()
            }
          )
        }
        LoadingBodyModel(
          id = DemoCodeTrackerScreenId.DEMO_MODE_CODE_SUBMISSION,
          message = "Submitting demo mode code...",
          onBack = null
        ).asModalScreen()
      }
    }
  }
}

sealed interface DemoModeState {
  data object DemoCodeEntryIdleState : DemoModeState

  data object DemoCodeEntrySubmissionState : DemoModeState
}
