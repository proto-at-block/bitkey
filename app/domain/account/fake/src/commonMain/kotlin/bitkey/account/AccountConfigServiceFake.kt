package bitkey.account

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

class AccountConfigServiceFake : AccountConfigService {
  private val lock = Mutex()
  private val activeOrDefaultConfig = MutableStateFlow<AccountConfig>(DefaultAccountConfigFake)
  private val activeConfig = MutableStateFlow<AccountConfig?>(null)
  private val defaultConfig = MutableStateFlow(DefaultAccountConfigFake)

  suspend fun setActiveConfig(config: AccountConfig?) {
    lock.withLock {
      activeConfig.value = config
      if (config == null) {
        activeOrDefaultConfig.value = defaultConfig.value
      } else {
        activeOrDefaultConfig.value = config
      }
    }
  }

  override fun activeOrDefaultConfig(): StateFlow<AccountConfig> {
    return activeOrDefaultConfig
  }

  override fun defaultConfig(): StateFlow<DefaultAccountConfig> {
    return defaultConfig
  }

  private suspend fun updateDefaultConfig(config: (DefaultAccountConfig) -> DefaultAccountConfig) {
    lock.withLock {
      defaultConfig.update {
        val newDefaultConfig = config(it)
        // If there is no active account, also update `activeOrDefaultConfig`
        if (activeConfig.value == null) {
          activeOrDefaultConfig.value = newDefaultConfig
        }
        newDefaultConfig
      }
    }
  }

  override suspend fun setBitcoinNetworkType(value: BitcoinNetworkType): Result<Unit, Error> {
    updateDefaultConfig { it.copy(bitcoinNetworkType = value) }
    return Ok(Unit)
  }

  override suspend fun setUsingSocRecFakes(value: Boolean): Result<Unit, Error> {
    updateDefaultConfig { it.copy(isUsingSocRecFakes = value) }
    return Ok(Unit)
  }

  override suspend fun setIsHardwareFake(value: Boolean): Result<Unit, Error> {
    updateDefaultConfig { it.copy(isHardwareFake = value) }
    return Ok(Unit)
  }

  override suspend fun setIsTestAccount(value: Boolean): Result<Unit, Error> {
    updateDefaultConfig { it.copy(isTestAccount = value) }
    return Ok(Unit)
  }

  override suspend fun setF8eEnvironment(value: F8eEnvironment): Result<Unit, Error> {
    updateDefaultConfig { it.copy(f8eEnvironment = value) }
    return Ok(Unit)
  }

  override suspend fun setDelayNotifyDuration(value: Duration?): Result<Unit, Error> {
    updateDefaultConfig { it.copy(delayNotifyDuration = value) }
    return Ok(Unit)
  }

  override suspend fun setSkipCloudBackupOnboarding(value: Boolean): Result<Unit, Error> {
    updateDefaultConfig { it.copy(skipCloudBackupOnboarding = value) }
    return Ok(Unit)
  }

  override suspend fun setSkipNotificationsOnboarding(value: Boolean): Result<Unit, Error> {
    updateDefaultConfig { it.copy(skipNotificationsOnboarding = value) }
    return Ok(Unit)
  }

  override suspend fun disableDemoMode(): Result<Unit, Error> {
    updateDefaultConfig { it.copy(isHardwareFake = true, isTestAccount = true) }
    return Ok(Unit)
  }

  override suspend fun enableDemoMode(): Result<Unit, Error> {
    updateDefaultConfig { it.copy(isHardwareFake = false, isTestAccount = false) }
    return Ok(Unit)
  }

  suspend fun reset() {
    lock.withLock {
      activeConfig.value = null
      activeOrDefaultConfig.value = DefaultAccountConfigFake
      defaultConfig.value = DefaultAccountConfigFake
    }
  }
}
