package bitkey.f8e.privilegedactions

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class DelayNotifyPeriodF8eClientFake : DelayNotifyPeriodF8eClient {
  var periodDays: Int = 7
  val getDelayNotifyPeriodCalls = mutableListOf<FullAccountId>()
  val setDelayNotifyPeriodCalls = mutableListOf<FullAccountId>()

  override suspend fun getDelayNotifyPeriod(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Int, NetworkingError> {
    getDelayNotifyPeriodCalls += fullAccountId
    return Ok(periodDays)
  }

  override suspend fun setDelayNotifyPeriod(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    delayPeriodDays: Int,
    proof: PrivilegedActionProof?,
  ): Result<Int, NetworkingError> {
    setDelayNotifyPeriodCalls += fullAccountId
    periodDays = delayPeriodDays
    return Ok(delayPeriodDays)
  }

  fun reset() {
    periodDays = 7
    getDelayNotifyPeriodCalls.clear()
    setDelayNotifyPeriodCalls.clear()
  }
}
