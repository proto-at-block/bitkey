package build.wallet.recovery.keyset

import build.wallet.worker.AppWorker

/**
 * Worker that periodically checks whether the active spending keyset matches between app and server.
 *
 * Runs at startup and when the app returns to foreground.
 */
interface SpendingKeysetRepairWorker : AppWorker
