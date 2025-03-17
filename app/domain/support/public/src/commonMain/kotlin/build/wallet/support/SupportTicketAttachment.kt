package build.wallet.support

import build.wallet.platform.data.MimeType
import okio.Source

sealed interface SupportTicketAttachment {
  val name: String
  val mimeType: MimeType
  val data: suspend () -> Source?

  data class Media(
    override val name: String,
    override val mimeType: MimeType,
    override val data: suspend () -> Source?,
  ) : SupportTicketAttachment

  data class Logs(
    override val name: String,
    override val data: suspend () -> Source?,
  ) : SupportTicketAttachment {
    override val mimeType: MimeType = MimeType.TEXT_PLAIN
  }
}
