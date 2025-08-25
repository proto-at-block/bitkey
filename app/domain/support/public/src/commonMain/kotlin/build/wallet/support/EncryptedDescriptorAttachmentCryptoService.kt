package build.wallet.support

import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Result

/*
 * Service for encrypting wallet descriptors and uploading them as encrypted attachments.
 */
interface EncryptedDescriptorAttachmentCryptoService {
  /**
   * Encrypts the wallet descriptor and uploads it as an encrypted attachment.
   *
   * @param accountId The account ID for which the descriptor is being encrypted.
   * @param spendingKeysets The list of spending keysets
   * @return Result containing the encryptedAttachmentId or an error.
   */
  suspend fun encryptAndUploadDescriptor(
    accountId: AccountId,
    spendingKeysets: List<SpendingKeyset>,
  ): Result<String, Error>
}
