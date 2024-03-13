@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.money.exchange

import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.Money
import build.wallet.money.currency.Currency
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.Instant

class CurrencyConverterImpl(
  private val accountRepository: AccountRepository,
  private val exchangeRateDao: ExchangeRateDao,
  private val f8eExchangeRateService: F8eExchangeRateService,
) : CurrencyConverter {
  override fun convert(
    fromAmount: Money,
    toCurrency: Currency,
    atTime: Instant?,
  ): Flow<Money?> {
    if (fromAmount.currency == toCurrency) {
      return flowOf(fromAmount)
    }

    // If the caller didn't give a specific time, just return the flow of current rates
    if (atTime == null) {
      return convert(fromAmount, toCurrency)
    }

    return flow {
      // First, try to look up the historical exchange rate in the database
      val historicalRates =
        exchangeRateDao.historicalExchangeRatesAtTime(atTime)
          .orEmpty()
          .filter { matchingCurrencies(it, fromAmount.currency, toCurrency) }

      if (historicalRates.isNotEmpty()) {
        emit(convert(fromAmount, toCurrency, rates = historicalRates))
      } else {
        // If it doesn't exist in the database, try to get it from bitstamp

        // In the meantime while we are making a network request to fetch the rate,
        // show the current rate
        emit(convert(fromAmount, toCurrency).first())
        val account =
          when (val accountStatus = accountRepository.accountStatus().first().get()) {
            null -> null
            else -> AccountStatus.accountFromAccountStatus(accountStatus)
          }

        when (account) {
          // We show values based on current rates if we do not have an account set up. This should
          // also be unreachable for now since we do not need historical rates until after a user
          // already exists.
          null -> emitAll(convert(fromAmount, toCurrency))
          else -> {
            f8eExchangeRateService.getHistoricalBtcExchangeRates(
              f8eEnvironment = account.config.f8eEnvironment,
              accountId = account.accountId,
              currencyCode = toCurrency.textCode.code,
              timestamps = immutableListOf(atTime)
            ).onSuccess { historicalExchangeRates ->
              historicalExchangeRates.forEach { historicalRate ->
                // If we successfully fetch the rates from F8e, store them in the db for future
                exchangeRateDao.storeHistoricalExchangeRate(historicalRate, atTime)
              }

              val convertedAmount =
                convert(fromAmount, toCurrency, rates = historicalExchangeRates)
              if (convertedAmount != null) {
                emit(convertedAmount)
              } else {
                // If there was an issue using the historical rates, fall back to the current rate
                emitAll(convert(fromAmount, toCurrency))
              }
            }.onFailure {
              // If there was an issue getting the rate, just emit the current rate instead
              // TODO (W-1733): Support retrying this endpoint. Currently will only retry on subsequent app launches / txn detail screens
              emitAll(convert(fromAmount, toCurrency))
            }
          }
        }
      }
    }.distinctUntilChanged()
  }

  private fun matchingCurrencies(
    exchangeRate: ExchangeRate,
    currency1: Currency,
    currency2: Currency,
  ): Boolean {
    // Check if currencies match, without loss of generality.
    val currencies = setOf(currency1.textCode, currency2.textCode)
    return currencies.contains(exchangeRate.fromCurrency) && currencies.contains(exchangeRate.toCurrency)
  }

  override fun convert(
    fromAmount: Money,
    toCurrency: Currency,
    rates: List<ExchangeRate>,
  ): Money? {
    if (fromAmount.currency == toCurrency) {
      return fromAmount
    }

    // If the forward rate exists, use that
    rates.firstOrNull {
      it.fromCurrency == fromAmount.currency.textCode &&
        it.toCurrency == toCurrency.textCode
    }?.let {
      return Money.money(
        currency = toCurrency,
        value =
          fromAmount.value.multiply(
            other = it.rate.toBigDecimal(),
            decimalMode = toCurrency.decimalMode()
          )
      )
    }

    // Otherwise, try the inverse conversion
    rates.firstOrNull {
      it.fromCurrency == toCurrency.textCode &&
        it.toCurrency == fromAmount.currency.textCode
    }?.let {
      return Money.money(
        currency = toCurrency,
        value =
          fromAmount.value.divide(
            other = it.rate.toBigDecimal(),
            decimalMode = toCurrency.decimalMode()
          )
      )
    }

    return null
  }

  private fun convert(
    fromAmount: Money,
    toCurrency: Currency,
  ): Flow<Money?> {
    if (fromAmount.currency == toCurrency) {
      return flowOf(fromAmount)
    }

    return exchangeRateDao.allExchangeRates()
      .mapLatest { convert(fromAmount, toCurrency, rates = it) }
      .distinctUntilChanged()
  }
}
