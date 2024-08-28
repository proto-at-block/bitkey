@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.debug

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.DebugOptionsEntity
import build.wallet.f8e.F8eEnvironment
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.mapResult
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration

class DebugOptionsServiceImpl(
  private val bitkeyDatabaseProvider: BitkeyDatabaseProvider,
  private val defaultDebugOptionsDecider: DefaultDebugOptionsDecider,
) : DebugOptionsService {
  private val database by lazy {
    bitkeyDatabaseProvider.database()
  }

  override fun options(): Flow<DebugOptions> {
    return database.debugOptionsQueries.options()
      .asFlowOfOneOrNull()
      .mapResult { it?.toDebugOptions() ?: defaultDebugOptionsDecider.options }
      .transformLatest {
        when {
          it.isOk -> emit(it.value)
          it.isErr -> {
            log(throwable = it.error) { "Error reading debug options from db, using default options." }
            // Fallback on default options
            emit(defaultDebugOptionsDecider.options)
          }
        }
      }
      .distinctUntilChanged()
  }

  override suspend fun setBitcoinNetworkType(value: BitcoinNetworkType): Result<Unit, Error> {
    return updateOptions { it.copy(bitcoinNetworkType = value) }
  }

  override suspend fun setIsHardwareFake(value: Boolean): Result<Unit, Error> {
    return updateOptions { it.copy(isHardwareFake = value) }
  }

  override suspend fun setIsTestAccount(value: Boolean): Result<Unit, Error> {
    return updateOptions { it.copy(isTestAccount = value) }
  }

  override suspend fun setUsingSocRecFakes(value: Boolean): Result<Unit, Error> {
    return updateOptions { it.copy(isUsingSocRecFakes = value) }
  }

  override suspend fun setF8eEnvironment(value: F8eEnvironment): Result<Unit, Error> {
    return updateOptions { it.copy(f8eEnvironment = value) }
  }

  override suspend fun setSkipCloudBackupOnboarding(value: Boolean): Result<Unit, Error> {
    return updateOptions { it.copy(skipCloudBackupOnboarding = value) }
  }

  override suspend fun setSkipNotificationsOnboarding(value: Boolean): Result<Unit, Error> {
    return updateOptions { it.copy(skipNotificationsOnboarding = value) }
  }

  override suspend fun setDelayNotifyDuration(value: Duration?): Result<Unit, Error> {
    return updateOptions { it.copy(delayNotifyDuration = value) }
  }

  private suspend fun updateOptions(
    block: (currentOptions: DebugOptions) -> DebugOptions,
  ): Result<Unit, Error> =
    database.awaitTransaction {
      val currentOptions = debugOptionsQueries.options().executeAsOneOrNull()
        ?.toDebugOptions()
        ?: defaultDebugOptionsDecider.options

      val updatedOptions = block(currentOptions)
      debugOptionsQueries.setOptions(
        bitcoinNetworkType = updatedOptions.bitcoinNetworkType,
        fakeHardware = updatedOptions.isHardwareFake,
        f8eEnvironment = updatedOptions.f8eEnvironment,
        isTestAccount = updatedOptions.isTestAccount,
        isUsingSocRecFakes = updatedOptions.isUsingSocRecFakes,
        delayNotifyDuration = updatedOptions.delayNotifyDuration,
        skipNotificationsOnboarding = updatedOptions.skipNotificationsOnboarding,
        skipCloudBackupOnboarding = updatedOptions.skipCloudBackupOnboarding
      )
    }.logFailure { "Error updating debug options in db." }
}

private fun DebugOptionsEntity.toDebugOptions(): DebugOptions {
  return DebugOptions(
    bitcoinNetworkType = bitcoinNetworkType,
    isHardwareFake = fakeHardware,
    f8eEnvironment = f8eEnvironment,
    isTestAccount = isTestAccount,
    isUsingSocRecFakes = isUsingSocRecFakes,
    delayNotifyDuration = delayNotifyDuration,
    skipNotificationsOnboarding = skipNotificationsOnboarding,
    skipCloudBackupOnboarding = skipCloudBackupOnboarding
  )
}
