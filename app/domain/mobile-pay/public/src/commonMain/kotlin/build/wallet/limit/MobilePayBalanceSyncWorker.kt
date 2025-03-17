package build.wallet.limit

import build.wallet.worker.AppWorker

/**
 * Periodically re-syncs the latest mobile pay balance with F8e. [MobilePayService.mobilePayData] will emit a
 * new [MobilePayData] if the status changes.
 */
interface MobilePayBalanceSyncWorker : AppWorker
