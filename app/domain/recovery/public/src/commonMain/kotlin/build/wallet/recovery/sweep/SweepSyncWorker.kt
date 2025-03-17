package build.wallet.recovery.sweep

import build.wallet.worker.AppWorker

/**
 * Worker that periodically checks whether the customer needs to perform a sweep
 * transaction (in case if they send some funds to an inactive wallet).
 * A status is emitted to [SweepService.sweepRequired] when the sweep is needed.
 */
interface SweepSyncWorker : AppWorker
