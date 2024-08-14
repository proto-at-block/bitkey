package build.wallet.debug

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

class DebugOptionsServiceFake : DebugOptionsService {
  private val options = MutableStateFlow(DebugOptionsFake)

  override fun options(): Flow<DebugOptions> {
    return options
  }

  override suspend fun setBitcoinNetworkType(value: BitcoinNetworkType): Result<Unit, Error> {
    options.value = options.value.copy(bitcoinNetworkType = value)
    return Ok(Unit)
  }

  override suspend fun setUsingSocRecFakes(value: Boolean): Result<Unit, Error> {
    options.value = options.value.copy(isUsingSocRecFakes = value)
    return Ok(Unit)
  }

  override suspend fun setIsHardwareFake(value: Boolean): Result<Unit, Error> {
    options.value = options.value.copy(isHardwareFake = value)
    return Ok(Unit)
  }

  override suspend fun setIsTestAccount(value: Boolean): Result<Unit, Error> {
    options.value = options.value.copy(isTestAccount = value)
    return Ok(Unit)
  }

  override suspend fun setF8eEnvironment(value: F8eEnvironment): Result<Unit, Error> {
    options.value = options.value.copy(f8eEnvironment = value)
    return Ok(Unit)
  }

  override suspend fun setDelayNotifyDuration(value: Duration?): Result<Unit, Error> {
    options.value = options.value.copy(delayNotifyDuration = value)
    return Ok(Unit)
  }

  override suspend fun setSkipCloudBackupOnboarding(value: Boolean): Result<Unit, Error> {
    options.value = options.value.copy(skipCloudBackupOnboarding = value)
    return Ok(Unit)
  }

  override suspend fun setSkipNotificationsOnboarding(value: Boolean): Result<Unit, Error> {
    options.value = options.value.copy(skipNotificationsOnboarding = value)
    return Ok(Unit)
  }

  fun reset() {
    options.value = DebugOptionsFake
  }
}
