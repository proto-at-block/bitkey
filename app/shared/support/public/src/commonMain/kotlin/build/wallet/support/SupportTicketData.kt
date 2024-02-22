package build.wallet.support

import build.wallet.email.Email

/**
 * Contains data for a support ticket form.
 */
interface SupportTicketData {
  val email: Email
  val sendDebugData: Boolean
  val attachments: List<SupportTicketAttachment>

  val fields: Set<SupportTicketField<*>>

  operator fun <Value : Any> get(field: SupportTicketField<Value>): Value?

  fun <Value : Any> getRawValue(field: SupportTicketField<Value>): SupportTicketField.RawValue? {
    val value = this[field] ?: return null
    return field.rawValueFrom(value)
  }

  fun asMap(): Map<SupportTicketField<*>, Any>

  fun asRawValueMap(): Map<SupportTicketField<*>, SupportTicketField.RawValue> =
    asMap()
      .mapValues { (field, value) ->
        // Since the type of the field has to match type of value, we can cast it.
        @Suppress("UNCHECKED_CAST")
        (field as SupportTicketField<Any>).rawValueFrom(value)
      }

  object Empty : SupportTicketData {
    override val email = Email("")
    override val sendDebugData: Boolean = true
    override val attachments: List<SupportTicketAttachment> = emptyList()
    override val fields: Set<SupportTicketField<*>> = emptySet()

    override fun <Value : Any> get(field: SupportTicketField<Value>): Value? = null

    override fun asMap(): Map<SupportTicketField<*>, Any> = emptyMap()
  }
}
