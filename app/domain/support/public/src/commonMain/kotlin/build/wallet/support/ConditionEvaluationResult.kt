package build.wallet.support

/**
 * Result of evaluating [SupportTicketField] conditions.
 * A field can either be [Hidden] or [Visible].
 * When a field is [Hidden], the app doesn't consider it a part of the form.
 * When a field is [Visible], it can either be [Visible.Required] or [Visible.Optional].
 * For [Visible.Optional], the value isn't being validated before submitting the field.
 */
sealed interface ConditionEvaluationResult {
  sealed interface Visible : ConditionEvaluationResult {
    data object Required : Visible

    data object Optional : Visible

    companion object {
      operator fun invoke(isRequired: Boolean): Visible =
        if (isRequired) {
          Required
        } else {
          Optional
        }
    }
  }

  data object Hidden : ConditionEvaluationResult
}
