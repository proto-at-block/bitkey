package build.wallet.notifications

import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

class RegisterWatchAddressQueueImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : RegisterWatchAddressQueue {
  override suspend fun append(item: RegisterWatchAddressContext): Result<Unit, Error> {
    return databaseProvider.database().awaitTransaction {
      registerWatchAddressQueueQueries.append(
        item.address,
        item.f8eSpendingKeyset.keysetId,
        item.accountId,
        item.f8eEnvironment
      )
    }
  }

  override suspend fun take(num: Int): Result<List<RegisterWatchAddressContext>, Error> {
    require(num >= 0) { "Requested element count $num is less than zero." }

    return databaseProvider.database()
      .registerWatchAddressQueueQueries.take(num.toLong())
      .awaitAsListResult()
      .map { items ->
        items.map {
          RegisterWatchAddressContext(
            it.address,
            F8eSpendingKeyset(
              keysetId = it.spendingKeysetId,
              spendingPublicKey = it.serverKey
            ),
            it.accountId,
            it.f8eEnvironment
          )
        }
      }
  }

  override suspend fun removeFirst(num: Int): Result<Unit, Error> {
    require(num >= 0) { "Requested element count $num is less than zero." }

    return databaseProvider.database().awaitTransaction {
      registerWatchAddressQueueQueries.removeFirst(num.toLong())
    }
  }
}
