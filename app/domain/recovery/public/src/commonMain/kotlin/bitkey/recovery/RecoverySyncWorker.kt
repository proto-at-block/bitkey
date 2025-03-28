package bitkey.recovery

import build.wallet.worker.AppWorker

/**
 * Periodically pulls Recovery status from f8e:
 * - when we have an active full account (with Lost Hardware recovery or in case if there's a Recovery Conflict)
 * - when we don't have an active full account but have active recovery (Lost App + Cloud recovery)
 */
interface RecoverySyncWorker : AppWorker
