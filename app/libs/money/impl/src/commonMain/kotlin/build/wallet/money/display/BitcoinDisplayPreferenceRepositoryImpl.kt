package build.wallet.money.display

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@BitkeyInject(AppScope::class)
class BitcoinDisplayPreferenceRepositoryImpl(
  appScope: CoroutineScope,
  private val bitcoinDisplayPreferenceDao: BitcoinDisplayPreferenceDao,
) : BitcoinDisplayPreferenceRepository {
  /**
   * If the user has explicitly set a preference (non-null DAO value), that wins.
   * Otherwise [BitcoinDisplayUnit.Bitcoin] is used as the default.
   */
  override val bitcoinDisplayUnit: StateFlow<BitcoinDisplayUnit> =
    bitcoinDisplayPreferenceDao.bitcoinDisplayPreference()
      .map { daoValue -> daoValue ?: BitcoinDisplayUnit.Bitcoin }
      .distinctUntilChanged()
      .stateIn(appScope, started = SharingStarted.Eagerly, initialValue = BitcoinDisplayUnit.Bitcoin)

  override suspend fun setBitcoinDisplayUnit(
    bitcoinDisplayUnit: BitcoinDisplayUnit,
  ): Result<Unit, Error> {
    return bitcoinDisplayPreferenceDao.setBitcoinDisplayPreference(bitcoinDisplayUnit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    return bitcoinDisplayPreferenceDao.clear()
  }
}
