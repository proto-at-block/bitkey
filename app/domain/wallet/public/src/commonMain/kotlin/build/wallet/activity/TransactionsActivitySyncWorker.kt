package build.wallet.activity

import build.wallet.worker.AppWorker

/**
 * App worker that periodically syncs transactions activity and emits into [TransactionsActivityService.transactionsState].
 */
interface TransactionsActivitySyncWorker : AppWorker
