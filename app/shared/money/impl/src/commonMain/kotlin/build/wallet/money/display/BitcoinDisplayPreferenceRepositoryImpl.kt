package build.wallet.money.display

import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

class BitcoinDisplayPreferenceRepositoryImpl(
  appScope: CoroutineScope,
  private val bitcoinDisplayPreferenceDao: BitcoinDisplayPreferenceDao,
) : BitcoinDisplayPreferenceRepository {
  private val defaultUnit = BitcoinDisplayUnit.Satoshi

  override val bitcoinDisplayUnit: StateFlow<BitcoinDisplayUnit> =
    bitcoinDisplayPreferenceDao
      .bitcoinDisplayPreference()
      .filterNotNull()
      .stateIn(appScope, started = SharingStarted.Lazily, initialValue = defaultUnit)

  override suspend fun setBitcoinDisplayUnit(
    bitcoinDisplayUnit: BitcoinDisplayUnit,
  ): Result<Unit, Error> {
    return bitcoinDisplayPreferenceDao.setBitcoinDisplayPreference(bitcoinDisplayUnit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    return bitcoinDisplayPreferenceDao.clear()
  }
}
