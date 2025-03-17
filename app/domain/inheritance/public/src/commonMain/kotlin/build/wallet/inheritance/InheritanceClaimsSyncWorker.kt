package build.wallet.inheritance

import build.wallet.worker.AppWorker

/**
 * Periodically syncs inheritance claims from f8e and makes them available via [InheritanceService].
 */
interface InheritanceClaimsSyncWorker : AppWorker
