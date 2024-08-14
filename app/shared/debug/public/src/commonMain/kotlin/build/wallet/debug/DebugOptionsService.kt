package build.wallet.debug

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Domain service for managing debug options.
 *
 * The options are stored in local database and can be updated using debug menu.
 * Other services and components can use this service to read the options to
 * determine the behavior of the app (e.g. what bitcoin network to use for a new wallet).
 */
interface DebugOptionsService {
  /**
   * Emits the current debug options stored in the local database.
   */
  fun options(): Flow<DebugOptions>

  suspend fun setBitcoinNetworkType(value: BitcoinNetworkType): Result<Unit, Error>

  suspend fun setUsingSocRecFakes(value: Boolean): Result<Unit, Error>

  suspend fun setIsTestAccount(value: Boolean): Result<Unit, Error>

  suspend fun setIsHardwareFake(value: Boolean): Result<Unit, Error>

  suspend fun setF8eEnvironment(value: F8eEnvironment): Result<Unit, Error>

  suspend fun setDelayNotifyDuration(value: Duration?): Result<Unit, Error>

  suspend fun setSkipCloudBackupOnboarding(value: Boolean): Result<Unit, Error>

  suspend fun setSkipNotificationsOnboarding(value: Boolean): Result<Unit, Error>
}
