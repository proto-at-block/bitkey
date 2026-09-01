package bitkey.securitycenter

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.getOrThrow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@BitkeyInject(AppScope::class)
class DelayNotifyConfigurationDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : DelayNotifyConfigurationDao {
  override fun getDelayNotifyPeriod(accountId: FullAccountId): Flow<Int?> {
    return flow {
      emitAll(
        databaseProvider.database()
          .delayNotifyConfigurationEntityQueries
          .get(accountId)
          .asFlowOfOneOrNull()
          .map { result ->
            result.getOrThrow()?.periodDays?.toInt()
          }
      )
    }
  }

  override suspend fun setDelayNotifyPeriod(
    accountId: FullAccountId,
    delayPeriodDays: Int,
  ) {
    databaseProvider.database().awaitTransaction {
      delayNotifyConfigurationEntityQueries.set(
        accountId = accountId,
        periodDays = delayPeriodDays.toLong()
      )
    }.getOrThrow()
  }

  override suspend fun clear() {
    databaseProvider.database().awaitTransaction {
      delayNotifyConfigurationEntityQueries.clear()
    }.getOrThrow()
  }
}
