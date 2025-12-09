package build.wallet.f8e.support

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.SupportTicketClientErrorCode
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.Source

class SupportTicketF8eClientMock(
  private val ticketFormDTO: TicketFormDTO,
) : SupportTicketF8eClient {
  override suspend fun createTicket(
    f8eEnvironment: F8eEnvironment,
    ticket: CreateTicketDTO,
  ): Result<Unit, F8eError<SupportTicketClientErrorCode>> {
    return Ok(Unit)
  }

  override suspend fun getFormStructure(
    f8eEnvironment: F8eEnvironment,
  ): Result<TicketFormDTO, NetworkingError> {
    return Ok(ticketFormDTO)
  }

  override suspend fun uploadAttachment(
    f8eEnvironment: F8eEnvironment,
    filename: String,
    mimeType: MimeType,
    source: Source,
  ): Result<String, NetworkingError> {
    return Ok("123456")
  }
}
