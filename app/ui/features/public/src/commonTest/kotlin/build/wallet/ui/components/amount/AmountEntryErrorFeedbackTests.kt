package build.wallet.ui.components.amount

import build.wallet.amount.KeypadButton
import build.wallet.platform.haptics.HapticsEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AmountEntryErrorFeedbackTests : FunSpec({
  test("delete only triggers shake when delete is rejected") {
    amountEntryKeypadFeedback(
      keypadButton = KeypadButton.Delete,
      isButtonPressRejected = false,
      shouldTriggerContextualErrorFeedback = true
    ).shouldShake.shouldBe(false)

    amountEntryKeypadFeedback(
      keypadButton = KeypadButton.Delete,
      isButtonPressRejected = true,
      shouldTriggerContextualErrorFeedback = false
    ).shouldShake.shouldBe(true)
  }

  test("non-delete input triggers shake for contextual or rejected feedback") {
    amountEntryKeypadFeedback(
      keypadButton = KeypadButton.Digit.One,
      isButtonPressRejected = false,
      shouldTriggerContextualErrorFeedback = false
    ).hapticsEffect.shouldBe(HapticsEffect.Selection)

    amountEntryKeypadFeedback(
      keypadButton = KeypadButton.Digit.One,
      isButtonPressRejected = true,
      shouldTriggerContextualErrorFeedback = false
    ).shouldShake.shouldBe(true)

    amountEntryKeypadFeedback(
      keypadButton = KeypadButton.Decimal,
      isButtonPressRejected = false,
      shouldTriggerContextualErrorFeedback = true
    ).shouldShake.shouldBe(true)
  }
})
