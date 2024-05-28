package build.wallet.partnerships

import app.cash.turbine.Turbine
import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onSubscription

internal class PartnershipTransactionsDaoMock(
  val saveCalls: Turbine<PartnershipTransaction>,
  val getTransactionsSubscriptions: Turbine<Unit>,
  val getByIdCalls: Turbine<PartnershipTransactionId>,
  val deleteTransactionCalls: Turbine<PartnershipTransactionId>,
  val clearCalls: Turbine<Unit>,
  var saveResult: Result<Unit, DbTransactionError> = Ok(Unit),
  var getByIdResult: Result<PartnershipTransaction?, DbTransactionError> = Ok(null),
  var deleteTransactionResult: Result<Unit, DbTransactionError> = Ok(Unit),
  var clearResult: Result<Unit, DbTransactionError> = Ok(Unit),
) : PartnershipTransactionsDao {
  val getTransactionsResults = MutableStateFlow<Result<List<PartnershipTransaction>, DbTransactionError>>(Ok(emptyList()))

  override suspend fun save(transaction: PartnershipTransaction): Result<Unit, DbTransactionError> {
    saveCalls.add(transaction)
    return saveResult
  }

  override fun getTransactions(): Flow<Result<List<PartnershipTransaction>, DbTransactionError>> {
    return getTransactionsResults.onSubscription {
      getTransactionsSubscriptions.add(Unit)
    }
  }

  override suspend fun getById(
    id: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, DbTransactionError> {
    getByIdCalls.add(id)
    return getByIdResult
  }

  override suspend fun deleteTransaction(
    transactionId: PartnershipTransactionId,
  ): Result<Unit, DbTransactionError> {
    deleteTransactionCalls.add(transactionId)
    return deleteTransactionResult
  }

  override suspend fun clear(): Result<Unit, DbTransactionError> {
    clearCalls.add(Unit)
    return clearResult
  }

  fun reset() {
    saveResult = Ok(Unit)
    getByIdResult = Ok(null)
    deleteTransactionResult = Ok(Unit)
    clearResult = Ok(Unit)
  }
}
