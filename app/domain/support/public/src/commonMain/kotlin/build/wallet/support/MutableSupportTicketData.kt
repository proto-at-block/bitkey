package build.wallet.support

import build.wallet.email.Email

interface MutableSupportTicketData : SupportTicketData {
  override var email: Email
  override var sendDebugData: Boolean

  override var sendEncryptedDescriptor: Boolean

  operator fun <Value : Any> set(
    field: SupportTicketField<Value>,
    value: Value,
  )

  fun addAttachment(attachment: SupportTicketAttachment)

  fun removeAttachment(attachment: SupportTicketAttachment)

  fun toImmutable(): SupportTicketData
}

fun buildSupportTicketData(builder: MutableSupportTicketData.() -> Unit): SupportTicketData {
  val mutableData = MapBackedMutableTicketData(SupportTicketData.Empty)
  mutableData.builder()
  return mutableData
}
