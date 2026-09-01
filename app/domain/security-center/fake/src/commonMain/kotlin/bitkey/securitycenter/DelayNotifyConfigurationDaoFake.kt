package bitkey.securitycenter

import build.wallet.bitkey.f8e.FullAccountId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class DelayNotifyConfigurationDaoFake : DelayNotifyConfigurationDao {
  private val periodsByAccount = MutableStateFlow<Map<FullAccountId, Int>>(emptyMap())

  override fun getDelayNotifyPeriod(accountId: FullAccountId): Flow<Int?> =
    periodsByAccount.map { it[accountId] }

  override suspend fun setDelayNotifyPeriod(
    accountId: FullAccountId,
    delayPeriodDays: Int,
  ) {
    periodsByAccount.value = periodsByAccount.value + (accountId to delayPeriodDays)
  }

  override suspend fun clear() {
    periodsByAccount.value = emptyMap()
  }

  fun reset() {
    periodsByAccount.value = emptyMap()
  }
}
