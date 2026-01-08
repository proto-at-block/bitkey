package build.wallet.recovery.keyset

import build.wallet.worker.AppWorker

/**
 * Worker that periodically checks keyset sync status between local and server.
 *
 * Runs at startup and when app returns to foreground to detect keyset mismatches
 * that may occur from recovering from a stale cloud backup.
 */
interface KeysetRepairWorker : AppWorker
