package build.wallet.support

/**
 * Represents structure of the support ticket form in [fields] and any [conditions].
 */
data class SupportTicketForm(
  val id: Long,
  val fields: List<SupportTicketField<*>>,
  val conditions: OptimizedSupportTicketFieldConditions,
) {
  private val knownFields: Map<SupportTicketField.KnownFieldType<*>, SupportTicketField<*>> =
    fields
      .filter { it.knownType != null }
      .associateBy {
        it.knownType as SupportTicketField.KnownFieldType<*>
      }

  /**
   * Get an instance of a field with the assigned [SupportTicketField.KnownFieldType],
   * or `null` if the form doesn't contain it.
   */
  operator fun <Field : SupportTicketField<*>> get(
    knownFieldType: SupportTicketField.KnownFieldType<Field>,
  ): Field? {
    @Suppress("UNCHECKED_CAST")
    return knownFields[knownFieldType] as? Field
  }
}
