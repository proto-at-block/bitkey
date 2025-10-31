package bitkey.recovery

import build.wallet.worker.AppWorker

/**
 * App worker that verifies descriptor backup health on app launch.
 * Checks if the active keyset has a valid descriptor backup on F8e.
 */
interface DescriptorBackupHealthSyncWorker : AppWorker
