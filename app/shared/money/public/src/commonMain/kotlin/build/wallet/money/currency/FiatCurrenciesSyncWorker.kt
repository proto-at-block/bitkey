package build.wallet.money.currency

import build.wallet.worker.AppWorker

/**
 * Worker that sync fiat currencies with the f8e server.
 * These are the currencies that are available to the customer to select as their primary
 * fiat currency in the settings.
 */
interface FiatCurrenciesSyncWorker : AppWorker
