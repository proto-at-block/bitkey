package build.wallet.support

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class SupportTicketRepositoryFake : SupportTicketRepository {
  override suspend fun createTicket(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    form: SupportTicketForm,
    data: SupportTicketData,
  ): Result<Unit, Error> {
    return Ok(Unit)
  }

  override suspend fun loadFormStructure(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<SupportTicketForm, Error> {
    TODO("Not yet implemented")
  }

  override suspend fun prefillKnownFields(form: SupportTicketForm): SupportTicketData {
    TODO("Not yet implemented")
  }
}
