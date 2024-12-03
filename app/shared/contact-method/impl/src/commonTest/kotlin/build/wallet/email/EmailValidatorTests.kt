package build.wallet.email

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class EmailValidatorTests : FunSpec({
  val emailValidator = EmailValidatorImpl()
  val validEmails =
    listOf(
      "email@example.com",
      "firstname.lastname@example.com",
      "email@subdomain.example.com",
      "firstname+lastname@example.com",
      "email@123.123.123.123",
      "1234567890@example.com",
      "email@example-one.com",
      "_______@example.com",
      "email@example.name",
      "email@example.museum",
      "email@example.co.jp",
      "firstname-lastname@example.com",
      "     firstname-lastname@example.com     "
    )

  val invalidEmails =
    listOf(
      "plainaddress",
      "#@%^%#$@#$@#.com",
      "@example.com",
      "Joe Smith <email@example.com>",
      "email.example.com",
      "email@example@example.com",
      "あいうえお@example.com",
      " email@example.com (Joe Smith)",
      "email@example..com"
    )

  test("valid email addresses are all valid") {
    validEmails.forEach {
      val email = Email(it)
      emailValidator.validateEmail(email).shouldBeTrue()
    }
  }

  test("invalid email address are all invalid") {
    invalidEmails.forEach {
      val email = Email(it)
      emailValidator.validateEmail(email).shouldBeFalse()
    }
  }
})
