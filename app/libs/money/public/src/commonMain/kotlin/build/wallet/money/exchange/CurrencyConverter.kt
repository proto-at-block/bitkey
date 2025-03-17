package build.wallet.money.exchange

import build.wallet.money.Money
import build.wallet.money.currency.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface CurrencyConverter {
  /**
   * Returns a [Money] amount converted to the given [Currency] from the given [Money].
   *
   * Updates to the currency converter's underlying exchange rate source will NOT be propagated
   * here, so consumers should observe changes emitted from [ExchangeRateDao] and pass the
   * new rates here if they want to update UI based on exchange rates.
   *
   * Will return null if the currency converter doesn't have a conversion between the currency of
   * [fromAmount] and [toCurrency].
   *
   * Will return [fromAmount] if the currency is equal to [toCurrency].
   *
   * To keep exchange rates up to date, use [ExchangeRateService] and collect emissions from
   * [ExchangeRateDao]. (The syncer will automatically update the dao).
   */
  fun convert(
    fromAmount: Money,
    toCurrency: Currency,
    rates: List<ExchangeRate>,
  ): Money?

  /**
   * Returns a stream of [Money] amounts converted to the given [Currency] from the given [Money]
   * at the given time. The historical rate will be a static rate, but this returns a flow in the
   * case that a null time is passed in or in the case that the historical rate cannot be accessed
   * (falls back to the behavior of the above method).
   */
  fun convert(
    fromAmount: Money,
    toCurrency: Currency,
    atTime: Instant?,
  ): Flow<Money?>

  fun latestRateTimestamp(
    fromCurrency: Currency,
    toCurrency: Currency,
  ): Flow<Instant?>
}
