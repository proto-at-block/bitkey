package build.wallet.partnerships

import build.wallet.worker.AppWorker

/**
 * Periodically syncs status of all currently pending partnership transactions.
 */
interface PartnershipTransactionsSyncWorker : AppWorker
