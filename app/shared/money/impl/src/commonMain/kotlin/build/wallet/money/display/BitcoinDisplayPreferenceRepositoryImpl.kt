package build.wallet.money.display

import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class BitcoinDisplayPreferenceRepositoryImpl(
  private val bitcoinDisplayPreferenceDao: BitcoinDisplayPreferenceDao,
) : BitcoinDisplayPreferenceRepository {
  private val defaultUnit = BitcoinDisplayUnit.Satoshi
  private val internalFlow = MutableStateFlow(defaultUnit)

  override val bitcoinDisplayUnit: StateFlow<BitcoinDisplayUnit>
    get() = internalFlow.asStateFlow()

  override fun launchSync(scope: CoroutineScope) {
    scope.launch {
      bitcoinDisplayPreferenceDao.bitcoinDisplayPreference()
        .filterNotNull()
        .collect(internalFlow)
    }
  }

  override suspend fun setBitcoinDisplayUnit(bitcoinDisplayUnit: BitcoinDisplayUnit) {
    bitcoinDisplayPreferenceDao.setBitcoinDisplayPreference(bitcoinDisplayUnit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    return bitcoinDisplayPreferenceDao.clear()
  }
}
