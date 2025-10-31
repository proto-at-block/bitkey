package build.wallet.cloud.backup.health

import build.wallet.worker.AppWorker

/**
 * Periodically syncs cloud backup health status (App Key backup and EEK backup) and
 * attempts to repair issues automatically when possible.
 *
 * Monitors cloud backup status and updates [CloudBackupHealthRepository.appKeyBackupStatus]
 * and [CloudBackupHealthRepository.eekBackupStatus] flows with the latest health information.
 *
 * Only runs when the app is in the foreground to avoid network failures when backgrounded.
 */
interface CloudBackupHealthSyncWorker : AppWorker
