package build.wallet.availability

import build.wallet.worker.AppWorker

/**
 * A worker that syncs the [AppFunctionalityStatus], the status is emitted by
 * [AppFunctionalityService.status].
 */
interface AppFunctionalitySyncWorker : AppWorker
