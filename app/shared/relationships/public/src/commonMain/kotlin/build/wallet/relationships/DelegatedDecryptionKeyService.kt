package build.wallet.relationships

import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.SealedData
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result
import okio.ByteString

interface DelegatedDecryptionKeyService {
  /**
   * Uploads the sealed delegated decryption key data to F8e
   *
   * @param sealedData the SealedData to upload. Re-uploading will replace the existing data.
   */
  suspend fun uploadSealedDelegatedDecryptionKeyData(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    sealedData: SealedData,
  ): Result<Unit, Error>

  /**
   * Gets the sealed delegated decryption key data from F8e. Will use the current active
   * account if no account id is specified
   *
   * @param AccountId the account id to use
   * @param F8eEnvironment the f8e environment to use
   */
  suspend fun getSealedDelegatedDecryptionKeyData(
    accountId: AccountId? = null,
    f8eEnvironment: F8eEnvironment? = null,
  ): Result<SealedData, Error>

  /**
   * Restores the delegated decryption key from the unsealed data
   *
   * @param unsealedData the unsealed data to restore the key from
   */
  suspend fun restoreDelegatedDecryptionKey(
    unsealedData: ByteString,
  ): Result<Unit, RelationshipsKeyError>
}
