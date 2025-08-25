package build.wallet.f8e.support

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.HttpError.UnhandledException
import build.wallet.ktor.result.NetworkingError
import build.wallet.support.EncryptedAttachment
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import okio.ByteString.Companion.toByteString

class EncryptedAttachmentF8eClientMock(
  var defaultCreateEncryptedAttachmentResult: Result<EncryptedAttachment, NetworkingError> =
    Err(UnhandledException(NotImplementedError())),
  var defaultUploadSealedAttachmentResult: Result<Unit, NetworkingError> =
    Err(UnhandledException(NotImplementedError())),
) : EncryptedAttachmentF8eClient {
  var createEncryptedAttachmentResult = defaultCreateEncryptedAttachmentResult
  var uploadSealedAttachmentResult = defaultUploadSealedAttachmentResult

  override suspend fun createEncryptedAttachment(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<EncryptedAttachment, NetworkingError> {
    return createEncryptedAttachmentResult
  }

  override suspend fun uploadSealedAttachment(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    encryptedAttachmentId: String,
    sealedAttachment: String,
  ): Result<Unit, NetworkingError> {
    return uploadSealedAttachmentResult
  }

  fun reset() {
    createEncryptedAttachmentResult = defaultCreateEncryptedAttachmentResult
    uploadSealedAttachmentResult = defaultUploadSealedAttachmentResult
  }

  companion object {
    val testEncryptedAttachment = EncryptedAttachment(
      encryptedAttachmentId = "test-attachment-id",
      publicKey = ByteArray(33) { it.toByte() }.toByteString()
    )
  }
}
