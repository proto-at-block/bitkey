package build.wallet.email

class EmailValidatorMock : EmailValidator {
  var isValid = false

  override fun validateEmail(email: Email): Boolean = isValid

  fun reset() {
    isValid = false
  }
}
