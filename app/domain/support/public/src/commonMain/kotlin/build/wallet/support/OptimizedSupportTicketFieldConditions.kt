package build.wallet.support

internal typealias ConditionChildField = SupportTicketField<*>
internal typealias ConditionParentField = SupportTicketField<*>

/**
 * SupportTicketField conditions with structure optimized for querying by child field.
 *
 * By default, we receive conditions in form of `if (parent == value) { children }`.
 * However, we need to check if a child field is visible and required.
 * So we transform the original structure into one,
 * where we can get all conditions applied to a specific child field.
 */
data class OptimizedSupportTicketFieldConditions(
  private val conditions: Map<ConditionChildField, ChildFieldConditions>,
) {
  fun evaluate(
    field: ConditionChildField,
    data: SupportTicketData,
  ): ConditionEvaluationResult {
    // If this field doesn't have an assigned condition, it's always visible ...
    val fieldConditions =
      conditions[field] ?: return ConditionEvaluationResult.Visible(
        // ... and the field's `isRequired` determines if it's required or optional.
        isRequired = field.isRequired
      )

    var isVisible = false
    // We need to go through all the conditions, to check if any marks it as required.
    fieldConditions.parentToExpectedValues.forEach { (parent, possibleValues) ->
      // A condition is only valid, if the parent field is visible, otherwise we skip it.
      if (evaluate(parent, data) is ConditionEvaluationResult.Visible) {
        val parentValue = data.getRawValue(parent)
        // If the current parent value is one of the `isRequiredIfAny` ...
        if (possibleValues.isRequiredIfAny.contains(parentValue)) {
          // ... then we can short-circuit as "Visible and Required" is a final state.
          return ConditionEvaluationResult.Visible.Required
        } else if (possibleValues.isOptionalIfAny.contains(parentValue)) {
          // ... if it's just in the `isOptionalIfAny`, we mark the field as visible and continue.
          isVisible = true
        }
      }
    }

    // Note that the `field.isRequired` is overridden by the condition and we no longer consider it.
    return if (isVisible) {
      ConditionEvaluationResult.Visible.Optional
    } else {
      ConditionEvaluationResult.Hidden
    }
  }

  data class ChildFieldConditions(
    val parentToExpectedValues: Map<ConditionParentField, ExpectedValues>,
  )

  data class ExpectedValues(
    val isRequiredIfAny: Set<SupportTicketField.RawValue>,
    val isOptionalIfAny: Set<SupportTicketField.RawValue>,
  )
}

data class SupportTicketFieldCondition(
  val parentField: SupportTicketField<*>,
  val expectedValue: SupportTicketField.RawValue,
  val children: List<Child>,
) {
  data class Child(
    val field: SupportTicketField<*>,
    val isRequired: Boolean,
  )
}

fun List<SupportTicketFieldCondition>.optimize(): OptimizedSupportTicketFieldConditions {
  val conditions =
    mutableMapOf<ConditionChildField, MutableMap<ConditionParentField, Pair<MutableSet<SupportTicketField.RawValue>, MutableSet<SupportTicketField.RawValue>>>>()
  forEach { condition ->
    condition.children.forEach { child ->
      val (required, optional) =
        conditions.getOrPut(child.field) {
          mutableMapOf()
        }.getOrPut(condition.parentField) {
          mutableSetOf<SupportTicketField.RawValue>() to mutableSetOf()
        }
      if (child.isRequired) {
        required.add(condition.expectedValue)
      } else {
        optional.add(condition.expectedValue)
      }
    }
  }
  return OptimizedSupportTicketFieldConditions(
    conditions.mapValues { (_, parentToExpectedValues) ->
      OptimizedSupportTicketFieldConditions.ChildFieldConditions(
        parentToExpectedValues =
          parentToExpectedValues.mapValues { (_, expectedValues) ->
            OptimizedSupportTicketFieldConditions.ExpectedValues(
              isRequiredIfAny = expectedValues.first,
              isOptionalIfAny = expectedValues.second
            )
          }
      )
    }
  )
}
