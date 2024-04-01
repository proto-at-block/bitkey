package build.wallet.bitcoin.transactions

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OutgoingTransactionDetailRepositoryMock(
  turbine: (name: String) -> Turbine<Unit>,
) : OutgoingTransactionDetailRepository {
  val setTransactionCalls = turbine.invoke("setTransaction calls")

  override suspend fun persistDetails(details: OutgoingTransactionDetail): Result<Unit, DbError> {
    setTransactionCalls += Unit
    return Ok(Unit)
  }
}
