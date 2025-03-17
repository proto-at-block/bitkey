package bitkey.account

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * Service for managing account configuration.
 *
 * Use this service to retrieve:
 * - The active account configuration ([AccountConfig]), if available.
 * - The default configuration ([DefaultAccountConfig]) for account creation or recovery.
 *
 * In non-Customer builds, default configurations can be overridden (e.g. for debug purposes).
 */
interface AccountConfigService {
  /**
   * Emits the currently effective account configuration.
   *
   * If an active account exists, it emits its configuration
   * ([AccountConfig]); otherwise, it emits the default ([DefaultAccountConfig]).
   */
  fun activeOrDefaultConfig(): StateFlow<AccountConfig>

  /**
   * Emits the default account configuration ([DefaultAccountConfig]).
   *
   * This is used for account creation and recovery scenarios when there is no active account.
   */
  fun defaultConfig(): StateFlow<DefaultAccountConfig>

  suspend fun setBitcoinNetworkType(value: BitcoinNetworkType): Result<Unit, Error>

  suspend fun setUsingSocRecFakes(value: Boolean): Result<Unit, Error>

  suspend fun setIsTestAccount(value: Boolean): Result<Unit, Error>

  suspend fun setIsHardwareFake(value: Boolean): Result<Unit, Error>

  suspend fun setF8eEnvironment(value: F8eEnvironment): Result<Unit, Error>

  suspend fun setDelayNotifyDuration(value: Duration?): Result<Unit, Error>

  suspend fun setSkipCloudBackupOnboarding(value: Boolean): Result<Unit, Error>

  suspend fun setSkipNotificationsOnboarding(value: Boolean): Result<Unit, Error>
}
