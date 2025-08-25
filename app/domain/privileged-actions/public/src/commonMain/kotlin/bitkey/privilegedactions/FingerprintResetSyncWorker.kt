package bitkey.privilegedactions

import build.wallet.worker.AppWorker

/**
 * A simple sync worker that warms the cache tracking when a fingerprint
 * reset is in progress.
 *
 * Useful to ensure the correct recommended actions are shown in Security Hub
 * on first load, instead of the Complete Fingerprint Reset card loading in after
 * other recommendations.
 */
interface FingerprintResetSyncWorker : AppWorker
