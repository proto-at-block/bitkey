package build.wallet.ui.app.partnerships

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import build.wallet.amount.KeypadButton
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.partnerships.purchase.CustomAmountBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.amount.AmountEntryKeypadFeedback
import build.wallet.ui.components.amount.AnimatedHeroAmount
import build.wallet.ui.components.amount.amountEntryKeypadFeedback
import build.wallet.ui.components.amount.rememberAmountEntryShakeOffset
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.keypad.Keypad
import build.wallet.ui.components.toolbar.AmountEntryToolbar
import build.wallet.ui.components.toolbar.amountEntryBackgroundColor
import build.wallet.ui.tokens.LabelType

@Composable
fun CustomAmountScreen(
  modifier: Modifier = Modifier,
  model: CustomAmountBodyModel,
) {
  val horizontalPadding = 20.dp
  val isDesignSystemV2Enabled = true
  var lastKeypadFeedback by remember { mutableStateOf<AmountEntryKeypadFeedback?>(null) }
  var keypadPressCount by remember { mutableIntStateOf(0) }
  val feedbackForKeypadButton = { keypadButton: KeypadButton ->
    amountEntryKeypadFeedback(
      keypadButton = keypadButton,
      isButtonPressRejected = model.keypadModel.isButtonPressRejected(keypadButton),
      shouldTriggerContextualErrorFeedback = model.isAmountAboveMaximum
    )
  }
  val amountShakeOffsetPx =
    rememberAmountEntryShakeOffset(
      trigger =
        if (isDesignSystemV2Enabled && lastKeypadFeedback?.shouldShake == true) {
          keypadPressCount
        } else {
          null
        },
      hapticsEffect = HapticsEffect.Reject
    )
  FormScreen(
    modifier = modifier,
    onBack = model.onBack,
    background = amountEntryBackgroundColor(),
    horizontalPadding = 0,
    toolbarContent = {
      AmountEntryToolbar(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        model = model.toolbar
      )
    },
    mainContent = {
      Column(
        modifier = Modifier.weight(1F)
      ) {
        Spacer(Modifier.weight(1F))
        AnimatedHeroAmount(
          modifier =
            Modifier.align(CenterHorizontally)
              .graphicsLayer { translationX = amountShakeOffsetPx }
              .padding(horizontal = 20.dp)
              .clipToBounds(),
          primaryAmount = model.amountModel.primaryAmount,
          primaryAmountGhostedSubstringRange = model.amountModel.primaryAmountGhostedSubstringRange,
          primaryAmountLabelType = LabelType.Display1,
          contextLine = model.amountModel.secondaryAmount,
          contextLineTreatment = model.amountContextLineTreatment,
          centerWhenDesignSystemV2 = true,
          onSwapClick = null,
          disabled = false
        )
        Spacer(Modifier.weight(1F))

        Keypad(
          modifier =
            Modifier.padding(horizontal = if (isDesignSystemV2Enabled) horizontalPadding else 0.dp),
          showDecimal = model.keypadModel.showDecimal,
          hapticsEffectForButtonPress = { keypadButton ->
            feedbackForKeypadButton(keypadButton).hapticsEffect
          },
          onButtonPress = rememberTrackingKeypadPresses(model.keypadModel, onKeypadButtonPressed = {
            lastKeypadFeedback = feedbackForKeypadButton(it)
            keypadPressCount += 1
          })
        )
      }
    },
    footerContent = {
      Button(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        model = model.primaryButton
      )
    }
  )
}

@Composable
private fun rememberTrackingKeypadPresses(
  keypadModel: KeypadModel,
  onKeypadButtonPressed: (KeypadButton) -> Unit,
): (KeypadButton) -> Unit {
  return remember(keypadModel, onKeypadButtonPressed) {
    { keypadButton ->
      onKeypadButtonPressed(keypadButton)
      keypadModel.onButtonPress(keypadButton)
    }
  }
}
