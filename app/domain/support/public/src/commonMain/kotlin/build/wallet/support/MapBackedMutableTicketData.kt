package build.wallet.support

import build.wallet.email.Email
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

internal class MapBackedMutableTicketData(
  initialData: SupportTicketData,
) : MutableSupportTicketData {
  override var email: Email = initialData.email
  override var sendDebugData: Boolean = initialData.sendDebugData
  override var sendEncryptedDescriptor: Boolean = initialData.sendEncryptedDescriptor
  private val mutableAttachments = initialData.attachments.toMutableList()
  override val attachments: List<SupportTicketAttachment> = mutableAttachments

  private val values = mutableMapOf<SupportTicketField<*>, Any>()
  override val fields: Set<SupportTicketField<*>> get() = values.keys

  override fun <Value : Any> get(field: SupportTicketField<Value>): Value? {
    @Suppress("UNCHECKED_CAST")
    return values[field] as Value?
  }

  override fun <Value : Any> set(
    field: SupportTicketField<Value>,
    value: Value,
  ) {
    values[field] = value
  }

  override fun addAttachment(attachment: SupportTicketAttachment) {
    mutableAttachments.add(attachment)
  }

  override fun removeAttachment(attachment: SupportTicketAttachment) {
    mutableAttachments.remove(attachment)
  }

  override fun toImmutable(): SupportTicketData =
    ImmutableSupportTicketData(
      email = email,
      sendDebugData = sendDebugData,
      sendEncryptedDescriptor = sendEncryptedDescriptor,
      attachments = attachments.toImmutableList(),
      data = values.toImmutableMap()
    )

  override fun asMap(): Map<SupportTicketField<*>, Any> = values
}
