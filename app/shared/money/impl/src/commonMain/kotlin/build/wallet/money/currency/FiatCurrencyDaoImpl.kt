package build.wallet.money.currency

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.FiatCurrencyEntity
import build.wallet.logging.logFailure
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.sqldelight.asFlowOfList
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class FiatCurrencyDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : FiatCurrencyDao {
  private suspend fun database() = databaseProvider.database()

  override fun allFiatCurrencies() =
    flow {
      databaseProvider.database()
        .fiatCurrencyQueries
        .allFiatCurrencies()
        .asFlowOfList()
        .map { result ->
          result.logFailure { "Failed to read all FiatCurrency values from database" }
          result.get()?.map { it.toFiatCurrency() } ?: emptyList()
        }
        .collect(::emit)
    }

  override fun fiatCurrency(textCode: IsoCurrencyTextCode) =
    flow {
      databaseProvider.database()
        .fiatCurrencyQueries
        .getFiatCurrencyByTextCode(textCode)
        .asFlowOfOneOrNull()
        .map { result ->
          result.logFailure { "Failed to read FiatCurrency from database for $textCode" }
          result.get()?.toFiatCurrency()
        }
        .collect(::emit)
    }

  override suspend fun storeFiatCurrencies(fiatCurrencies: List<FiatCurrency>) {
    database().fiatCurrencyQueries.awaitTransaction {
      // First, clear the current currencies
      clear()

      // Then insert the new currencies
      fiatCurrencies.forEach { fiatCurrency ->
        insertOrUpdateFiatCurrency(
          textCode = fiatCurrency.textCode,
          fractionalDigits = fiatCurrency.fractionalDigits.toLong(),
          displayUnitSymbol = fiatCurrency.unitSymbol,
          displayName = fiatCurrency.displayConfiguration.name,
          displayCountryCode = fiatCurrency.displayConfiguration.displayCountryCode
        )
      }
    }.logFailure { "Failed to store FiatCurrency objects: $fiatCurrencies" }
  }
}

private fun FiatCurrencyEntity.toFiatCurrency() =
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
