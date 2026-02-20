package build.wallet.support

import build.wallet.platform.data.MimeType
import okio.Source

/**
 * Maximum number of media attachments allowed per support ticket.
 * Service limits total attachments to 5, so we cap media at 4 to allow room
 * for the system-generated log file when sendDebugData is enabled.
 */
const val MAX_MEDIA_ATTACHMENTS = 4

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
