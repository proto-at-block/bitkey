package build.wallet.cloud.backup

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.sync.Mutex

/**
 * Lock used to ensure concurrency safety in cloud backup operations.
 * Prevents race conditions between workers and services that read and write cloud backups.
 *
 * **PROTECTED OPERATIONS:**
 * This lock coordinates three independent cloud backup writers:
 * 1. [CloudBackupHealthRepository.performSync] - syncs/repairs backup health
 * 2. [CloudBackupVersionMigrationWorker.executeWork] - migrates backup versions
 * 3. [SocRecCloudBackupSyncWorker.refreshCloudBackup] - updates trusted contacts
 *
 * **DEADLOCK PREVENTION:**
 * Code holding this lock must NEVER call any of the above methods, as this would cause
 * a deadlock (attempting to acquire the same lock twice).
 *
 * Current implementation is deadlock-free because:
 * - No operation calls another operation that holds this lock
 * - CloudBackupRepository operations don't acquire this lock
 * - No circular dependencies in the call graph
 *
 * **RACE CONDITIONS PREVENTED:**
 * - Migration overwrites SocRec trusted contact updates
 * - SocRec updates lost during health repair
 * - Concurrent writes causing corrupted backup state
 *
 * **Before modifying:** Verify no circular dependencies exist in the call graph.
 */
interface CloudBackupOperationLock : Mutex

@BitkeyInject(AppScope::class, boundTypes = [CloudBackupOperationLock::class])
class CloudBackupOperationLockImpl : CloudBackupOperationLock, Mutex by Mutex()
