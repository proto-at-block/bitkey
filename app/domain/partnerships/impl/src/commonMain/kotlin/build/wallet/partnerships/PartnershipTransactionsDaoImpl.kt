package build.wallet.partnerships

import app.cash.sqldelight.coroutines.asFlow
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbTransactionError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@BitkeyInject(AppScope::class)
class PartnershipTransactionsDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : PartnershipTransactionsDao {
  override suspend fun save(
    transaction: PartnershipTransaction,
  ): Result<Unit, DbTransactionError> {
    return databaseProvider.database().awaitTransaction {
      partnershipTransactionsQueries.saveEntity(transaction.toEntity())
    }
  }

  override fun getTransactions(): Flow<Result<List<PartnershipTransaction>, DbTransactionError>> {
    return flow {
      val database = databaseProvider.database()
      database.partnershipTransactionsQueries
        .getAll()
        .asFlow()
        .map { query ->
          database.awaitTransactionWithResult {
            query.executeAsList().map { it.toModel() }
          }
        }
        .collect(::emit)
    }
  }

  override fun getPreviouslyUsedPartnerIds(): Flow<Result<List<PartnerId>, DbTransactionError>> {
    return flow {
      val database = databaseProvider.database()
      database.partnershipTransactionsQueries
        .getPreviouslyUsedPartnerIds()
        .asFlow()
        .map { query ->
          database.awaitTransactionWithResult {
            query.executeAsList()
          }
        }
        .collect(::emit)
    }
  }

  override suspend fun getMostRecentByPartner(
    partnerId: PartnerId,
  ): Result<PartnershipTransaction?, DbTransactionError> {
    return databaseProvider.database().awaitTransactionWithResult {
      partnershipTransactionsQueries
        .getMostRecentTransactionByPartnerId(partnerId)
        .executeAsOneOrNull()
        ?.toModel()
    }
  }

  override suspend fun getById(
    id: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, DbTransactionError> {
    return databaseProvider.database().awaitTransactionWithResult {
      partnershipTransactionsQueries
        .getById(id)
        .executeAsOneOrNull()
        ?.toModel()
    }
  }

  override suspend fun deleteTransaction(
    transactionId: PartnershipTransactionId,
  ): Result<Unit, DbTransactionError> {
    return databaseProvider.database().awaitTransaction {
      partnershipTransactionsQueries.delete(transactionId)
    }
  }

  override suspend fun clear(): Result<Unit, DbTransactionError> {
    return databaseProvider.database().awaitTransaction {
      partnershipTransactionsQueries.clear()
    }
  }
}
