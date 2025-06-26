package build.wallet.statemachine.core.input

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.Capitalization
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import dev.zacsweers.redacted.annotations.Redacted

/**
 * Form body that prompts the user to enter a name.
 *
 * @param title - The screen title
 * @param subline - The screen subline, if applicable.
 * @param value - The current value of the name input field
 * @param placeholder - The placeholder text for the name input field when empty.
 * @param primaryButton - Model of the primary continue button
 * @param onValueChange - Function handler invoked once the user changes the field value
 * @param onClose - invoked once the screen is closed
 */
data class NameInputBodyModel(
  val title: String,
  val subline: String? = null,
  @Redacted
  val value: String = "",
  val placeholder: String = "Name",
  override val primaryButton: ButtonModel,
  val onValueChange: (String) -> Unit,
  val onClose: () -> Unit,
  override val id: EventTrackerScreenId?,
  val hasPreviousScreen: Boolean = false,
) : FormBodyModel(
    id = id,
    onSwipeToDismiss = onClose,
    onBack = onClose,
    toolbar = ToolbarModel(
      leadingAccessory = if (hasPreviousScreen) {
        ToolbarAccessoryModel.IconAccessory.BackAccessory(onClick = onClose)
      } else {
        ToolbarAccessoryModel.IconAccessory.CloseAccessory(onClick = onClose)
      }
    ),
    header = FormHeaderModel(headline = title, subline = subline),
    mainContentList = immutableListOf(
      FormMainContentModel.TextInput(
        fieldModel = TextFieldModel(
          value = value,
          placeholderText = placeholder,
          onValueChange = { newValue, _ -> onValueChange(newValue) },
          keyboardType = TextFieldModel.KeyboardType.Default,
          capitalization = Capitalization.Words,
          onDone = if (primaryButton.isEnabled) {
            { primaryButton.onClick.invoke() }
          } else {
            null
          }
        )
      )
    ),
    primaryButton = primaryButton
  )
