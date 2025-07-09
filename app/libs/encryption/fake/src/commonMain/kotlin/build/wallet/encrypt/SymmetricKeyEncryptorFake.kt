package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

class SymmetricKeyEncryptorFake : SymmetricKeyEncryptor {
  var sealNoMetadataResult: SealedData? = null
  var unsealNoMetadataResult: ByteString? = null
  var unsealError: Boolean = false

  override fun sealNoMetadata(
    unsealedData: ByteString,
    key: SymmetricKey,
  ) = sealNoMetadataResult ?: SealedData(
    ciphertext = unsealedData,
    nonce = key.raw,
    tag = ByteString.EMPTY
  )

  override fun unsealNoMetadata(
    sealedData: SealedData,
    key: SymmetricKey,
  ): ByteString {
    if (unsealError) {
      throw IllegalStateException()
    }
    return unsealNoMetadataResult ?: sealedData.ciphertext
  }

  override fun seal(
    unsealedData: ByteString,
    key: SymmetricKey,
    aad: ByteString,
  ): XCiphertext {
    return XCiphertext(unsealedData.utf8())
  }

  override fun unseal(
    ciphertext: XCiphertext,
    key: SymmetricKey,
    aad: ByteString,
  ): ByteString {
    if (unsealError) {
      throw IllegalStateException()
    }
    return ciphertext.value.encodeUtf8()
  }

  fun reset() {
    sealNoMetadataResult = null
    unsealNoMetadataResult = null
    unsealError = false
  }
}
