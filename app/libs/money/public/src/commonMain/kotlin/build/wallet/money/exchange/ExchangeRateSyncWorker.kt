package build.wallet.money.exchange

import build.wallet.worker.AppWorker

/**
 * Periodically pulls exchange rates and updates into local database cache.
 *
 * The latest exchange rates are emitted by [ExchangeRateService.exchangeRates].
 * Use [CurrencyConverter] to convert amounts using latest exchange rate.
 */
interface ExchangeRateSyncWorker : AppWorker
