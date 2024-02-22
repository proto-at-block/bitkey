package build.wallet.statemachine.limit

import build.wallet.analytics.events.screen.id.MobilePayEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun MobilePayOnboardingScreenModel(
  buttonsEnabled: Boolean,
  isLoading: Boolean,
  onContinue: () -> Unit,
  onSetUpLater: () -> Unit,
  onBack: () -> Unit,
) = FormBodyModel(
  onBack = onBack,
  onSwipeToDismiss = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onBack)
    ),
  header =
    FormHeaderModel(
      headline = "Enable mobile pay",
      subline =
        "Leave your device at home, and make small spends with just the key on your phone."
    ),
  primaryButton =
    ButtonModel(
      text = "Continue",
      size = Footer,
      isEnabled = buttonsEnabled,
      onClick = Click.standardClick { onContinue() }
    ),
  secondaryButton =
    ButtonModel(
      text = "Set up later",
      size = Footer,
      treatment = Secondary,
      isEnabled = buttonsEnabled,
      isLoading = isLoading,
      onClick = Click.standardClick { onSetUpLater() }
    ),
  id = MobilePayEventTrackerScreenId.ENABLE_MOBILE_PAY_INSTRUCTIONS
)
