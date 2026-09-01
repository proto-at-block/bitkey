package build.wallet.email

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class EmailValidatorImpl : EmailValidator {
  /**
   * This is the pattern used in the w3c html email standard
   * https://html.spec.whatwg.org/multipage/input.html#input.email.attrs.value.multiple
   */
  private val emailRegex =
    "^[a-zA-Z0-9.!#\$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+\$".toRegex()

  override fun validateEmail(email: Email): Boolean {
    return emailRegex.matches(email.value.trim())
  }
}
