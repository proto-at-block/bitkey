package build.wallet.f8e.support

import build.wallet.bitkey.f8e.AccountId
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
    accountId: AccountId,
    ticket: CreateTicketDTO,
  ): Result<Unit, NetworkingError> {
    return Ok(Unit)
  }

  override suspend fun getFormStructure(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<TicketFormDTO, NetworkingError> {
    return Ok(ticketFormDTO)
  }

  override suspend fun uploadAttachment(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    filename: String,
    mimeType: MimeType,
    source: Source,
  ): Result<String, NetworkingError> {
    return Ok("123456")
  }
}
