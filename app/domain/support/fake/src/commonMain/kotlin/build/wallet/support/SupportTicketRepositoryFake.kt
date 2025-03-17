package build.wallet.support

import build.wallet.bitkey.f8e.AccountId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class SupportTicketRepositoryFake : SupportTicketRepository {
  override suspend fun createTicket(
    accountId: AccountId,
    form: SupportTicketForm,
    data: SupportTicketData,
  ): Result<Unit, Error> {
    return Ok(Unit)
  }

  override suspend fun loadFormStructure(accountId: AccountId): Result<SupportTicketForm, Error> {
    TODO("Not yet implemented")
  }

  override suspend fun prefillKnownFields(form: SupportTicketForm): SupportTicketData {
    TODO("Not yet implemented")
  }
}
