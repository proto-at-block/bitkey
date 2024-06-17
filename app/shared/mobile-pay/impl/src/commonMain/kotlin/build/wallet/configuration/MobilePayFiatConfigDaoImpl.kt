package build.wallet.configuration

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.GetAllFiatCurrencyMobilePayConfigurations
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.sqldelight.asFlowOfList
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MobilePayFiatConfigDaoImpl(
  databaseProvider: BitkeyDatabaseProvider,
) : MobilePayFiatConfigDao {
  private val database by lazy { databaseProvider.database() }

  override fun allConfigurations() =
    database.fiatCurrencyMobilePayConfigurationQueries
      .getAllFiatCurrencyMobilePayConfigurations()
      .asFlowOfList()
      .map { result ->
        result.logFailure { "Failed to read all FiatMobilePayConfiguration values from database" }
        result.get()?.associate {
          val currency = it.toFiatCurrency()
          currency to it.toFiatMobilePayConfiguration(currency)
        } ?: emptyMap()
      }
      .distinctUntilChanged()

  override suspend fun storeConfigurations(
    configurations: Map<FiatCurrency, MobilePayFiatConfig>,
  ): Result<Unit, DbError> {
    return database.fiatCurrencyMobilePayConfigurationQueries.awaitTransaction {
      // First, clear the current values
      clear()

      // Then, store the given values
      configurations.forEach { entry ->
        insertOrUpdateFiatCurrencyMobilePayConfiguration(
          textCode = entry.key.textCode,
          minimumLimit = entry.value.minimumLimit.fractionalUnitValue.longValue(),
          maximumLimit = entry.value.maximumLimit.fractionalUnitValue.longValue(),
          snapValues =
            entry.value.snapValues
              .mapKeys { it.key.fractionalUnitValue.intValue() }
              .mapValues { it.value.value.fractionalUnitValue.intValue() }
        )
      }
    }.logFailure {
      "Failed to store FiatMobilePayConfigurations: $configurations"
    }
  }
}

private fun GetAllFiatCurrencyMobilePayConfigurations.toFiatMobilePayConfiguration(
  currency: FiatCurrency,
) = MobilePayFiatConfig(
  minimumLimit = FiatMoney(currency, fractionalUnitAmount = minimumLimit.toBigInteger()),
  maximumLimit = FiatMoney(currency, fractionalUnitAmount = maximumLimit.toBigInteger()),
  snapValues =
    snapValues
      .mapKeys { FiatMoney(currency, it.key.toBigInteger()) }
      .mapValues {
        MobilePayFiatConfig.SnapTolerance(FiatMoney(currency, it.value.toBigInteger()))
      }
)

private fun GetAllFiatCurrencyMobilePayConfigurations.toFiatCurrency() =
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
