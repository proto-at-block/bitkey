package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey
import okio.ByteString

/**
 * Encrypts and decrypts data using the authenticated encryption with
 * associated data cipher XChaCha20-Poly1305. A 24-byte nonce is required.
 * The AAD is optional.
 */
interface XChaCha20Poly1305 {
  @Throws(Error::class)
  fun encrypt(
    key: SymmetricKey,
    nonce: XNonce,
    plaintext: ByteString,
    aad: ByteString = ByteString.EMPTY,
  ): XCiphertext

  @Throws(Error::class)
  fun decrypt(
    key: SymmetricKey,
    ciphertextWithMetadata: XCiphertext,
    aad: ByteString = ByteString.EMPTY,
  ): ByteString

  // IMPORTANT: Prefer using `encrypt`.
  //
  // Encryption without XCiphertext metadata.
  @Throws(Error::class)
  fun encryptNoMetadata(
    key: SymmetricKey,
    plaintext: ByteString,
    nonce: ByteString,
    aad: ByteString = ByteString.EMPTY,
  ): SealedData

  // IMPORTANT: Prefer using `decrypt`.
  //
  // Decryption without XCiphertext metadata.
  @Throws(Error::class)
  fun decryptNoMetadata(
    key: SymmetricKey,
    sealedData: SealedData,
    aad: ByteString = ByteString.EMPTY,
  ): ByteString

  companion object {
    const val ALGORITHM = "XChaCha20Poly1305"
  }
}
