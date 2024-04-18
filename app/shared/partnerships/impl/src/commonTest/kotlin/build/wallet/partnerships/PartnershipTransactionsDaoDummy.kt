package build.wallet.partnerships

import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Empty/default implementation of [PartnershipTransactionsDao] for testing.
 */
internal class PartnershipTransactionsDaoDummy : PartnershipTransactionsDao {
  override suspend fun save(
    transaction: PartnershipTransaction,
  ): Result<Unit, DbTransactionError> = TODO()

  override fun getTransactions(): Flow<Result<List<PartnershipTransaction>, DbTransactionError>> =
    flow {
      TODO()
    }

  override suspend fun deleteTransaction(
    transactionId: PartnershipTransactionId,
  ): Result<Unit, DbTransactionError> = TODO()

  override suspend fun clear(): Result<Unit, DbTransactionError> = TODO()
}
