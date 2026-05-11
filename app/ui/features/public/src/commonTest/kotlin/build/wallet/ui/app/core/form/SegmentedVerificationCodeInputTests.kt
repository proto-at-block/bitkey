package build.wallet.ui.app.core.form

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class SegmentedVerificationCodeInputTests : FunSpec({
  test("sanitizes segmented verification code input to digits within the expected length") {
    sanitizeVerificationCodeInput(
      rawValue = "12a34\n567",
      expectedCodeLength = 6
    ).shouldBe("123456")
  }

  test("skips segmented verification code updates when sanitization does not change the value") {
    nextSanitizedVerificationCodeInput(
      currentValue = "123456",
      rawValue = "1234567",
      expectedCodeLength = 6
    ).shouldBeNull()
  }
})
