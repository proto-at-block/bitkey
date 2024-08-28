package build.wallet.bitcoin.transactions

import build.wallet.worker.AppWorker

/**
 * Caches the spending wallet for the currently logged-in account and periodically syncs
 * the latest [TransactionsData] for said wallet.
 */
interface TransactionSyncWorker : AppWorker
