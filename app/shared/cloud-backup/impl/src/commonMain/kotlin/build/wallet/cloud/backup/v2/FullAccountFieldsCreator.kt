package build.wallet.cloud.backup.v2

import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Result

/** Create a [FullAccountFields] object for cloud backup. */
interface FullAccountFieldsCreator {
  suspend fun create(
    keybox: Keybox,
    sealedCsek: SealedCsek,
    trustedContacts: List<TrustedContact>,
  ): Result<FullAccountFields, FullAccountFieldsCreationError>

  sealed class FullAccountFieldsCreationError : Error() {
    /**
     * App auth private key could not be retrieved from encrypted storage, either due to an
     * error in encrypted storage or the private key being missing.
     */
    data class AppAuthPrivateKeyRetrievalError(
      override val cause: Throwable,
    ) : FullAccountFieldsCreationError()

    /**
     * App spending private key could not be retrieved from encrypted storage, either due to an
     * error in AppPrivateKeyDao or the key's absence.
     */
    data class AppSpendingPrivateKeyRetrievalError(
      override val cause: Throwable,
    ) : FullAccountFieldsCreationError()

    /**
     * SocRec keys could not be retrieved from encrypted storage.
     */
    data class SocRecKeysRetrievalError(
      override val cause: Throwable,
    ) : FullAccountFieldsCreationError()

    /** Wraps JsonEncodingError for the [FullAccountKeys]. */
    data class KeysInfoEncodingError(
      override val cause: Throwable,
    ) : FullAccountFieldsCreationError()

    /** Error returned when encrypting the PKEK using TC keys fails */
    data class SocRecEncryptionError(
      override val cause: Throwable,
    ) : FullAccountFieldsCreationError()

    /** Unexpected error with encrypted key storage. */
    data class PkekRetrievalError(
      override val cause: Throwable? = null,
    ) : FullAccountFieldsCreationError()
  }
}
