package build.wallet.debug

import build.wallet.debug.cloud.CloudBackupStoreType
import build.wallet.debug.cloud.name

/**
 * Debug-only service for deleting selected local and cloud key material.
 */
interface DebugDataDeletionService {
  suspend fun delete(targets: List<DebugDataDeletionTarget>): DebugDataDeletionReport
}

sealed interface DebugDataDeletionTarget {
  data object ActiveAppSpendingKey : DebugDataDeletionTarget

  data object ActiveAppGlobalAuthKey : DebugDataDeletionTarget

  data object ActiveAppRecoveryAuthKey : DebugDataDeletionTarget

  data object AllLocalAppPrivateKeys : DebugDataDeletionTarget

  data object OnboardingAppKey : DebugDataDeletionTarget

  data object OnboardingKeyboxMaterial : DebugDataDeletionTarget

  data object LocalCsek : DebugDataDeletionTarget

  data object RelationshipsKeys : DebugDataDeletionTarget

  data object DescriptorBackupVerificationState : DebugDataDeletionTarget

  data object ActiveAccountCloudBackup : DebugDataDeletionTarget

  data object AllCloudBackupStores : DebugDataDeletionTarget

  data class CloudBackupsInStore(
    val storeType: CloudBackupStoreType,
  ) : DebugDataDeletionTarget

  data object AllLocalAppData : DebugDataDeletionTarget

  data object CorruptCloudBackup : DebugDataDeletionTarget

  data object CloudBackupActiveKeyset : DebugDataDeletionTarget
}

data class DebugDataDeletionReport(
  val deletedTargets: List<DebugDataDeletionTarget>,
  val failures: List<DebugDataDeletionFailure>,
) {
  val succeeded: Boolean = failures.isEmpty()
}

data class DebugDataDeletionFailure(
  val target: DebugDataDeletionTarget,
  val message: String,
)

val DebugDataDeletionTarget.displayName: String
  get() =
    when (this) {
      DebugDataDeletionTarget.ActiveAppSpendingKey -> "Active app spending key"
      DebugDataDeletionTarget.ActiveAppGlobalAuthKey -> "Active app global auth key"
      DebugDataDeletionTarget.ActiveAppRecoveryAuthKey -> "Active app recovery auth key"
      DebugDataDeletionTarget.AllLocalAppPrivateKeys -> "All local app private keys"
      DebugDataDeletionTarget.OnboardingAppKey -> "Onboarding app key"
      DebugDataDeletionTarget.OnboardingKeyboxMaterial -> "Onboarding keybox material"
      DebugDataDeletionTarget.LocalCsek -> "Local cloud encryption keys"
      DebugDataDeletionTarget.RelationshipsKeys -> "Trusted contact recovery keys"
      DebugDataDeletionTarget.DescriptorBackupVerificationState -> "Descriptor backup verification state"
      DebugDataDeletionTarget.ActiveAccountCloudBackup -> "Active account cloud backup"
      DebugDataDeletionTarget.AllCloudBackupStores -> "All cloud backup stores"
      is DebugDataDeletionTarget.CloudBackupsInStore -> "Cloud backups (${storeType.name})"
      DebugDataDeletionTarget.AllLocalAppData -> "All local app data"
      DebugDataDeletionTarget.CorruptCloudBackup -> "Corrupt cloud backup"
      DebugDataDeletionTarget.CloudBackupActiveKeyset -> "Cloud backup active keyset"
    }
