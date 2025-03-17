package build.wallet.activity

import build.wallet.worker.AppWorker

/**
 * App worker that periodically syncs transactions activity and emits into [TransactionsActivityService.transactions].
 */
interface TransactionsActivitySyncWorker : AppWorker
