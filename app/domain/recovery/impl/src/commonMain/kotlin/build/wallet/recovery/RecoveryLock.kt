package build.wallet.recovery

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.sync.Mutex

/**
 * Lock used to ensure concurrency safety in recovery domain operations.
 * Specifically, it prevents race conditions between service calls made
 * by the presentation layer and the sync worker.
 */
@BitkeyInject(AppScope::class)
class RecoveryLock : Mutex by Mutex()
