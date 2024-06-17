package build.wallet.configuration

import build.wallet.worker.AppWorker

/**
 * Worker to sync the latest Mobile Pay fiat configuration from f8e.
 */
interface MobilePayFiatConfigSyncWorker : AppWorker
