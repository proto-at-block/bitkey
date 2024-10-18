package build.wallet.support

class SupportTicketFormValidatorFake : SupportTicketFormValidator {
  override fun validate(
    form: SupportTicketForm,
    data: SupportTicketData,
  ): Boolean {
    return true
  }
}
