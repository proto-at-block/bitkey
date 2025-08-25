package build.wallet.f8e.support

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.support.EncryptedAttachment
import com.github.michaelbull.result.Result

/**
 * Client for interacting with encrypted attachments in customer feedback
 */
interface EncryptedAttachmentF8eClient {
  /**
   * Creates a new encrypted attachment with KMS-generated key pair.
   *
   * @param f8eEnvironment The F8e environment to use
   * @param accountId The account ID for the encrypted attachment
   * @return Result containing the encrypted attachment details or an error
   */
  suspend fun createEncryptedAttachment(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<EncryptedAttachment, NetworkingError>

  /**
   * Uploads a sealed attachment to an existing encrypted attachment.
   *
   * @param f8eEnvironment The F8e environment to use
   * @param accountId The account ID for the encrypted attachment
   * @param encryptedAttachmentId The ID of the encrypted attachment
   * @param sealedAttachment The sealed attachment data as a byte array
   * @return Result containing Unit on success or an error
   */
  suspend fun uploadSealedAttachment(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    encryptedAttachmentId: String,
    sealedAttachment: String,
  ): Result<Unit, NetworkingError>
}
