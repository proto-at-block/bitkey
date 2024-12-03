package build.wallet.email

/**
 * Validates an [Email] is in a valid format
 */
interface EmailValidator {
  /**
   * Validate email
   *
   * @param email - the email to validate
   * @return [Boolean] true when the email is valid, otherwise false
   */
  fun validateEmail(email: Email): Boolean
}
