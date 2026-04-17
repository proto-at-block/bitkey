package build.wallet.bitkey.hardware

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HardwareDisplayValidationTests : FunSpec({
  test("ASCII printable characters are displayable") {
    for (c in ' '..'~') {
      HardwareDisplayValidation.isHwDisplayable(c) shouldBe true
    }
  }

  test("Latin-1 accented characters are displayable") {
    for (name in listOf('À', 'É', 'ö', 'ü', 'ÿ')) {
      HardwareDisplayValidation.isHwDisplayable(name) shouldBe true
    }
  }

  test("Latin Extended characters are displayable") {
    HardwareDisplayValidation.isHwDisplayable('Ā') shouldBe true // Latin Extended-A
    HardwareDisplayValidation.isHwDisplayable('ǅ') shouldBe true // Latin Extended-B
  }

  test("emoji are not displayable") {
    HardwareDisplayValidation.isHwDisplayable("👋") shouldBe false
  }

  test("CJK characters are not displayable") {
    HardwareDisplayValidation.isHwDisplayable('田') shouldBe false
  }

  test("Cyrillic characters are not displayable") {
    HardwareDisplayValidation.isHwDisplayable('П') shouldBe false
  }

  test("NBSP and soft hyphen are not displayable") {
    HardwareDisplayValidation.isHwDisplayable('\u00A0') shouldBe false
    HardwareDisplayValidation.isHwDisplayable('\u00AD') shouldBe false
  }

  test("string validation respects max length") {
    val valid = "A".repeat(HardwareDisplayValidation.MAX_VALUE_LENGTH)
    HardwareDisplayValidation.isHwDisplayable(valid) shouldBe true

    val tooLong = "A".repeat(HardwareDisplayValidation.MAX_VALUE_LENGTH + 1)
    HardwareDisplayValidation.isHwDisplayable(tooLong) shouldBe false
  }

  test("empty and blank strings are displayable") {
    HardwareDisplayValidation.isHwDisplayable("") shouldBe true
    HardwareDisplayValidation.isHwDisplayable("   ") shouldBe true
  }
})
