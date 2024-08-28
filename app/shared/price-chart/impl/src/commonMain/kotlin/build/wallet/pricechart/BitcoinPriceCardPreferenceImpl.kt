package build.wallet.pricechart

import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.feature.flags.BitcoinPriceChartFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly

private const val PREF_DEFAULT = true

class BitcoinPriceCardPreferenceImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val eventTracker: EventTracker,
  private val bitcoinPriceChartFeatureFlag: BitcoinPriceChartFeatureFlag,
  appCoroutineScope: CoroutineScope,
) : BitcoinPriceCardPreference {
  private val db by lazy {
    databaseProvider.database()
  }

  override val isEnabled: StateFlow<Boolean> =
    bitcoinPriceChartFeatureFlag.flagValue()
      .combine(
        db.bitcoinPriceCardPreferenceQueries
          .getBitcoinPriceCardPreference()
          .asFlowOfOneOrNull()
          .map { it.get()?.enabled ?: PREF_DEFAULT }
      ) { featureFlagEnabled, preferenceEnabled ->
        featureFlagEnabled.value && preferenceEnabled
      }
      .stateIn(appCoroutineScope, Eagerly, false)

  override suspend fun get(): Result<Boolean, DbError> {
    return db.bitcoinPriceCardPreferenceQueries
      .getBitcoinPriceCardPreference()
      .awaitAsOneOrNullResult()
      .logFailure { "Unable to get bitcoin price card entity" }
      .map { (it?.enabled ?: PREF_DEFAULT) && bitcoinPriceChartFeatureFlag.isEnabled() }
  }

  override suspend fun set(enabled: Boolean): Result<Unit, DbError> {
    return db.bitcoinPriceCardPreferenceQueries
      .awaitTransactionWithResult {
        setBitcoinPriceCardPreference(enabled)
      }.onSuccess {
        if (enabled) {
          eventTracker.track(Action.ACTION_APP_BITCOIN_PRICE_CARD_ENABLED)
        } else {
          eventTracker.track(Action.ACTION_APP_BITCOIN_PRICE_CARD_DISABLED)
        }
      }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return db.bitcoinPriceCardPreferenceQueries
      .awaitTransactionWithResult {
        clear()
      }
  }
}
