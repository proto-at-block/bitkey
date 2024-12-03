package build.wallet.email

class EmailValidatorImpl : EmailValidator {
  /**
   * This is the pattern used in the w3c html email standard
   * https://html.spec.whatwg.org/multipage/input.html#input.email.attrs.value.multiple
   */
  private val emailPattern =
    "^[a-zA-Z0-9.!#\$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*\$"

  override fun validateEmail(email: Email): Boolean {
    return emailPattern.toRegex().matches(email.value.trim())
  }
}
