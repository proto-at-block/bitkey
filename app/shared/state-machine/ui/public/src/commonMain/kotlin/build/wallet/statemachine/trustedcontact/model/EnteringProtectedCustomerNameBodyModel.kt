package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.Capitalization
import build.wallet.ui.model.toolbar.ToolbarModel

fun EnteringProtectedCustomerNameBodyModel(
  value: String = "",
  onValueChange: (String) -> Unit,
  primaryButton: ButtonModel,
  retreat: Retreat,
) = FormBodyModel(
  id = SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME,
  onSwipeToDismiss = retreat.onRetreat,
  onBack = retreat.onRetreat,
  toolbar =
    ToolbarModel(
      leadingAccessory = retreat.leadingToolbarAccessory
    ),
  header =
    FormHeaderModel(
      headline = "Save their name",
      subline = "So you can remember who you’re helping protect."
    ),
  mainContentList =
    immutableListOf(
      FormMainContentModel.TextInput(
        fieldModel =
          TextFieldModel(
            value = value,
            placeholderText = "Name",
            onValueChange = { newValue, _ -> onValueChange(newValue) },
            keyboardType = TextFieldModel.KeyboardType.Default,
            capitalization = Capitalization.Words,
            onDone =
              if (primaryButton.isEnabled) {
                { primaryButton.onClick() }
              } else {
                null
              }
          )
      )
    ),
  primaryButton = primaryButton
)
