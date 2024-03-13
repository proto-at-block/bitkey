package build.wallet.cloud.backup

import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Result

interface FullAccountCloudBackupCreator {
  /**
   * Creates a backup for [Keybox] (always uses latest backup schema version) along with all of
   * its private keys.
   *
   * @param keybox to backup.
   * @param sealedCsek the sealed CSEK to use to encrypt the backup. Expected that at this point,
   * unsealed [Csek] is persisted in [CsekDao]. If not, returns [CsekMissing] error.
   * @param trustedContacts: list of trusted contacts to backup.
   */
  suspend fun create(
    keybox: Keybox,
    sealedCsek: SealedCsek,
    trustedContacts: List<TrustedContact>,
  ): Result<CloudBackup, FullAccountCloudBackupCreatorError>

  /**
   * Indicates an error during the process of backup encryption or creation.
   */
  sealed class FullAccountCloudBackupCreatorError : Error() {
    /** Error during full account fields creation. */
    data class FullAccountFieldsCreationError(
      override val cause: Throwable? = null,
    ) : FullAccountCloudBackupCreatorError()

    /** Error while retrieving app recovery auth keypair. */
    data class AppRecoveryAuthKeypairRetrievalError(
      override val cause: Throwable?,
    ) : FullAccountCloudBackupCreatorError()

    /** Error while encoding full account fields. */
    data class FullAccountFieldsEncodingError(
      override val cause: Throwable? = null,
    ) : FullAccountCloudBackupCreatorError()

    /**
     * SocRec keys could not be retrieved from encrypted storage.
     */
    data class SocRecKeysRetrievalError(
      override val cause: Throwable,
    ) : FullAccountCloudBackupCreatorError()

    /**
     * Error describing that the CSEK is missing from the [CsekDao].
     */
    data object CsekMissing : FullAccountCloudBackupCreatorError() {
      override val cause: Throwable? = null
    }
  }
}
