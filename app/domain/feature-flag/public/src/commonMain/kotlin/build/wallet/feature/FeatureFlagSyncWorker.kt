package build.wallet.feature

import build.wallet.worker.AppWorker

/**
 * App worker that:
 * - initializes feature flags on app launch by setting default values
 * - kicks off a sync of feature flags with remote values every time the app is resumed
 * (brought to the foreground)
 */
interface FeatureFlagSyncWorker : AppWorker
