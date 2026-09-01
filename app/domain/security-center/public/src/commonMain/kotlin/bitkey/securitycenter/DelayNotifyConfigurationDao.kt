package bitkey.securitycenter

import build.wallet.bitkey.f8e.FullAccountId
import kotlinx.coroutines.flow.Flow

interface DelayNotifyConfigurationDao {
  fun getDelayNotifyPeriod(accountId: FullAccountId): Flow<Int?>

  suspend fun setDelayNotifyPeriod(
    accountId: FullAccountId,
    delayPeriodDays: Int,
  )

  suspend fun clear()
}
