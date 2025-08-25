package build.wallet.statemachine.settings.full.feedback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import build.wallet.email.Email
import build.wallet.support.ImmutableSupportTicketData
import build.wallet.support.MutableSupportTicketData
import build.wallet.support.SupportTicketAttachment
import build.wallet.support.SupportTicketData
import build.wallet.support.SupportTicketField
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

class StateMapBackedSupportTicketData(
  private val initialData: SupportTicketData = SupportTicketData.Empty,
) : MutableSupportTicketData {
  private val data = mutableStateMapOf<SupportTicketField<*>, Any>()

  override var email: Email by mutableStateOf(initialData.email)
  override var sendDebugData: Boolean by mutableStateOf(initialData.sendDebugData)

  override var sendEncryptedDescriptor: Boolean by mutableStateOf(initialData.sendEncryptedDescriptor)
  private val mutableAttachments: MutableList<SupportTicketAttachment> = mutableStateListOf()
  override val attachments: List<SupportTicketAttachment> = mutableAttachments

  override val fields: Set<SupportTicketField<*>> get() = data.keys

  fun hasPendingChanges(): Boolean {
    return email != initialData.email ||
      attachments.toList() != initialData.attachments.toList() ||
      data.keys.toSet() != initialData.fields.toSet() ||
      data.any { (field, value) ->
        initialData[field] != value
      }
  }

  init {
    data.putAll(initialData.asMap())
  }

  override fun <Value : Any> get(field: SupportTicketField<Value>): Value? {
    return data[field] as Value?
  }

  override fun <Value : Any> set(
    field: SupportTicketField<Value>,
    value: Value,
  ) {
    data[field] = value
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
      data = data.toImmutableMap()
    )

  override fun asMap(): Map<SupportTicketField<*>, Any> = data
}
