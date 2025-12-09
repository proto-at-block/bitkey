package build.wallet.support

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class SupportTicketRepositoryFake : SupportTicketRepository {
  var createTicketResult: Result<Unit, SupportTicketError> = Ok(Unit)

  override suspend fun createTicket(
    form: SupportTicketForm,
    data: SupportTicketData,
  ): Result<Unit, SupportTicketError> {
    return createTicketResult
  }

  override suspend fun loadFormStructure(): Result<SupportTicketForm, Error> {
    TODO("Not yet implemented")
  }

  override suspend fun prefillKnownFields(form: SupportTicketForm): SupportTicketData {
    TODO("Not yet implemented")
  }

  fun reset() {
    createTicketResult = Ok(Unit)
  }
}
