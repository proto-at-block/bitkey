package build.wallet.money.display

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.FiatCurrencyPreference
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.money.currency.FiatCurrency
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapOr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FiatCurrencyPreferenceDaoImpl(
  databaseProvider: BitkeyDatabaseProvider,
) : FiatCurrencyPreferenceDao {
  private val database = databaseProvider.database()

  override fun fiatCurrencyPreference(): Flow<FiatCurrency?> {
    return database.fiatCurrencyPreferenceQueries.fiatCurrencyPreference().asFlowOfOneOrNull()
      .map { result ->
        result
          .logFailure { "Failed to read fiat currency preference" }
          .mapOr(null) { entity ->
            entity?.toFiatCurrency()
          }
      }
      .distinctUntilChanged()
  }

  override suspend fun setFiatCurrencyPreference(
    fiatCurrency: FiatCurrency,
  ): Result<Unit, DbError> {
    return database
      .awaitTransaction {
        fiatCurrencyPreferenceQueries.setFiatCurrencyPreference(fiatCurrency.textCode)
      }
      .logFailure { "Failed to set fiat currency preference" }
  }

  override suspend fun clear() =
    database
      .awaitTransaction { fiatCurrencyPreferenceQueries.clear() }
      .logFailure { "Failed to clear fiat currency preference" }
}

private fun FiatCurrencyPreference.toFiatCurrency() =
  FiatCurrency(
    textCode = textCode,
    unitSymbol = displayUnitSymbol,
    fractionalDigits = fractionalDigits.toInt(),
    displayConfiguration =
      FiatCurrency.DisplayConfiguration(
        name = displayName,
        displayCountryCode = displayCountryCode
      )
  )
