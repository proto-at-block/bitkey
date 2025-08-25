package build.wallet.support

import build.wallet.email.Email
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

data class ImmutableSupportTicketData(
  override val email: Email,
  override val sendDebugData: Boolean,
  override val sendEncryptedDescriptor: Boolean,
  override val attachments: ImmutableList<SupportTicketAttachment>,
  private val data: ImmutableMap<SupportTicketField<*>, Any>,
) : SupportTicketData {
  override val fields: ImmutableSet<SupportTicketField<*>> = data.keys

  override fun <Value : Any> get(field: SupportTicketField<Value>): Value? {
    @Suppress("UNCHECKED_CAST")
    return data[field] as Value?
  }

  override fun asMap(): Map<SupportTicketField<*>, Any> = data
}
