package build.wallet.support

/**
 * Used to validate user input against a form structure.
 */
interface SupportTicketFormValidator {
  /**
   * Checks if the supplied [data] are valid based on rules of [form] structure.
   *
   * TODO[W-5814]: Currently returns just whether the form is valid.
   *  Instead it should return which fields are invalid.
   */
  fun validate(
    form: SupportTicketForm,
    data: SupportTicketData,
  ): Boolean
}
