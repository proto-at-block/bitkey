package build.wallet.cloud.backup

import build.wallet.bitkey.app.AppRecoveryAuthKeypair
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.cloud.backup.v2.FullAccountFields
import build.wallet.cloud.backup.v2.SocRecV1AccountFeatures

/**
 * Cloud Backup Features relevant to the V1 Implementation of Social Recovery.
 */
interface SocRecV1BackupFeatures {
  /**
   * Whether this account is using Social Recovery Fake implementations
   * for testing.
   */
  val isUsingSocRecFakes: Boolean
  val appRecoveryAuthKeypair: AppRecoveryAuthKeypair
  val trustedContactIdentityKeypair: AppKey

  /**
   * Account backup used to restore and authenticate the account during recovery.
   */
  val fullAccountFields: FullAccountFields?
}

/**
 * Determine whether the cloud backup contains Social Recovery data
 * necessary to perform the recovery operation.
 */
val CloudBackup.socRecDataAvailable: Boolean get() {
  if (this !is SocRecV1BackupFeatures) return false
  fullAccountFields.let { account ->
    if (account !is SocRecV1AccountFeatures) return false

    return account.socRecEncryptionKeyCiphertextMap.isNotEmpty()
  }
}
