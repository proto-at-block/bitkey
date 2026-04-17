package build.wallet.money.display

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.DefaultBitcoinDisplayUnitFeatureFlag
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

@BitkeyInject(AppScope::class)
class BitcoinDisplayPreferenceRepositoryImpl(
  appScope: CoroutineScope,
  private val bitcoinDisplayPreferenceDao: BitcoinDisplayPreferenceDao,
  defaultBitcoinDisplayUnitFeatureFlag: DefaultBitcoinDisplayUnitFeatureFlag,
) : BitcoinDisplayPreferenceRepository {
  /**
   * Combines the DAO preference with the feature flag default. If the user has
   * explicitly set a preference (non-null DAO value), that wins. Otherwise the
   * feature flag value is used as the default — and because we observe the flag's
   * [StateFlow], the default updates reactively when LD syncs after app launch.
   */
  override val bitcoinDisplayUnit: StateFlow<BitcoinDisplayUnit> =
    combine(
      bitcoinDisplayPreferenceDao.bitcoinDisplayPreference(),
      defaultBitcoinDisplayUnitFeatureFlag.flagValue()
    ) { daoValue, flagValue ->
      daoValue ?: flagValue.resolveUnit()
    }
      .distinctUntilChanged()
      .stateIn(appScope, started = SharingStarted.Eagerly, initialValue = BitcoinDisplayUnit.Satoshi)

  override suspend fun setBitcoinDisplayUnit(
    bitcoinDisplayUnit: BitcoinDisplayUnit,
  ): Result<Unit, Error> {
    return bitcoinDisplayPreferenceDao.setBitcoinDisplayPreference(bitcoinDisplayUnit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    return bitcoinDisplayPreferenceDao.clear()
  }
}

/**
 * Resolves the flag value to a [BitcoinDisplayUnit].
 * Falls back to [BitcoinDisplayUnit.Satoshi] for unrecognized values.
 */
private fun FeatureFlagValue.StringFlag.resolveUnit(): BitcoinDisplayUnit {
  return when (value.uppercase()) {
    "BITCOIN" -> BitcoinDisplayUnit.Bitcoin
    else -> BitcoinDisplayUnit.Satoshi
  }
}
