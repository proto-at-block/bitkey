package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.toolbar.ToolbarModel

fun EnteringInviteCodeBodyModel(
  value: String = "",
  onValueChange: (String) -> Unit,
  primaryButton: ButtonModel,
  retreat: Retreat,
) = FormBodyModel(
  id = SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ENTER_INVITE_CODE,
  onSwipeToDismiss = retreat.onRetreat,
  onBack = retreat.onRetreat,
  toolbar =
    ToolbarModel(
      leadingAccessory = retreat.leadingToolbarAccessory
    ),
  header =
    FormHeaderModel(
      headline = "Enter invite code to accept",
      subline = "Use the code that your Trusted Contact sent you to help safeguard their wallet."
    ),
  mainContentList =
    immutableListOf(
      FormMainContentModel.TextInput(
        fieldModel =
          TextFieldModel(
            value = value,
            placeholderText = "Invite Code",
            onValueChange = { newValue, _ ->
              onValueChange(newValue.replace("-", "").chunked(4).joinToString("-"))
            },
            keyboardType = TextFieldModel.KeyboardType.Default,
            onDone =
              if (primaryButton.isEnabled) {
                primaryButton.onClick::invoke
              } else {
                null
              }
          )
      )
    ),
  primaryButton = primaryButton
)
