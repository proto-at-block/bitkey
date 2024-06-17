package build.wallet.f8e.support

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.Result
import okio.Source

interface SupportTicketF8eClient {
  /**
   * Creates a new support ticket.
   */
  suspend fun createTicket(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    ticket: CreateTicketDTO,
  ): Result<Unit, NetworkingError>

  /**
   * Returns a support form structure to be used.
   */
  suspend fun getFormStructure(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<TicketFormDTO, NetworkingError>

  /**
   * Uploads attachment and returns token of the attachment.
   *
   * @param filename Name of the uploaded file. Extension has to match mime type.
   * @param mimeType MIME type of the uploaded file (image/png, text/plain, etc.)
   * @param source Source of the uploaded file's data. Will be closed once uploaded.
   */
  suspend fun uploadAttachment(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    filename: String,
    mimeType: MimeType,
    source: Source,
  ): Result<String, NetworkingError>
}
