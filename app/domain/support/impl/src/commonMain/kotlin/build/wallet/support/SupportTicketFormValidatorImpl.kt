package build.wallet.support

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.email.EmailValidator

@BitkeyInject(AppScope::class)
class SupportTicketFormValidatorImpl(
  private val emailValidator: EmailValidator,
) : SupportTicketFormValidator {
  override fun validate(
    form: SupportTicketForm,
    data: SupportTicketData,
  ): Boolean {
    if (!emailValidator.validateEmail(data.email)) {
      return false
    }

    return form.fields.all { field ->
      if (form.conditions.evaluate(field, data) == ConditionEvaluationResult.Visible.Required) {
        field.validateFrom(data)
      } else {
        true
      }
    }
  }

  private fun <Value : Any> SupportTicketField<Value>.validateFrom(
    data: SupportTicketData,
  ): Boolean {
    val value = data[this] ?: return false
    return validate(value)
  }
}
