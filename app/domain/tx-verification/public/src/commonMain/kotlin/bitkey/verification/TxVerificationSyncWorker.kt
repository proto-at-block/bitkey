package bitkey.verification

import build.wallet.worker.AppWorker

/**
 * Sync the active transaction verification policy.
 *
 * This will initiate a sync of the users' transaction verification policy
 * at startup, when the account keybox is changed, or when the feature
 * flag is changed.
 */
interface TxVerificationSyncWorker : AppWorker
