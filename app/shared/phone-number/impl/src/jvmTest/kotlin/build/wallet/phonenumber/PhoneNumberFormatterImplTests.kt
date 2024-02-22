package build.wallet.phonenumber

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PhoneNumberFormatterImplTests : FunSpec({
  val formatter =
    PhoneNumberFormatterImpl(
      phoneNumberLibBindings = PhoneNumberLibBindingsImpl()
    )

  test("formats number without + character") {
    formatter.formatPartialPhoneNumber("123456789")
      .shouldBe("+1 234-567-89")
    formatter.formatPartialPhoneNumber("44771234")
      .shouldBe("+44 7712 34")
  }

  test("formats valid number") {
    formatter.formatPartialPhoneNumber("+33 6 12 345")
      .shouldBe("+33 6 12 34 5")
  }

  test("returns invalid number without format") {
    formatter.formatPartialPhoneNumber("999999")
      .shouldBe("999999")
  }
})
