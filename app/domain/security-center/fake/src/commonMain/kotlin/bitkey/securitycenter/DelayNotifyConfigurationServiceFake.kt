package bitkey.securitycenter

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class DelayNotifyConfigurationServiceFake : DelayNotifyConfigurationService {
  val periodDays = MutableStateFlow<Int?>(7)
  val setCalls = mutableListOf<SetDelayNotifyPeriodCall>()
  val syncCalls = mutableListOf<FullAccount>()
  var setResults = mutableListOf<Result<Int, NetworkingError>>()
  var syncResults = mutableListOf<Result<Int, NetworkingError>>()

  override fun delayNotifyPeriod(account: FullAccount): Flow<Int?> = periodDays

  override suspend fun syncDelayNotifyPeriod(account: FullAccount): Result<Int, NetworkingError> {
    syncCalls += account
    val result = if (syncResults.isEmpty()) {
      Ok(periodDays.value ?: 7)
    } else {
      syncResults.removeAt(0)
    }
    return result.onSuccess { periodDays.value = it }
  }

  override suspend fun setDelayNotifyPeriod(
    account: FullAccount,
    delayPeriodDays: Int,
    proof: PrivilegedActionProof?,
  ): Result<Int, NetworkingError> {
    setCalls += SetDelayNotifyPeriodCall(
      f8eEnvironment = account.config.f8eEnvironment,
      fullAccountId = account.accountId,
      delayPeriodDays = delayPeriodDays,
      proof = proof
    )

    val result = if (setResults.isEmpty()) {
      Ok(delayPeriodDays)
    } else {
      setResults.removeAt(0)
    }
    return result.onSuccess { periodDays.value = it }
  }

  fun reset() {
    periodDays.value = 7
    setCalls.clear()
    syncCalls.clear()
    setResults.clear()
    syncResults.clear()
  }
}

data class SetDelayNotifyPeriodCall(
  val f8eEnvironment: F8eEnvironment,
  val fullAccountId: FullAccountId,
  val delayPeriodDays: Int,
  val proof: PrivilegedActionProof?,
)
