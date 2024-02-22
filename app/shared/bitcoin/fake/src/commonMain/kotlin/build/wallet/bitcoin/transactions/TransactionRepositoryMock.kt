package build.wallet.bitcoin.transactions

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class TransactionRepositoryMock(
  turbine: (name: String) -> Turbine<Unit>,
) : TransactionRepository {
  val setTransactionCalls = turbine.invoke("setTransaction calls")

  override suspend fun setTransaction(transaction: Transaction): Result<Unit, DbError> {
    setTransactionCalls += Unit
    return Ok(Unit)
  }
}
