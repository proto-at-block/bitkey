package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey
import okio.ByteString

/**
 * Provides symmetric encryption and decryption services using XChaCha20-Poly1305 authenticated encryption.
 */
interface SymmetricKeyEncryptor {
  /**
   * Encrypts data without metadata using XChaCha20-Poly1305.
   *
   * This method generates a random 24-byte XChaCha20 nonce internally and returns
   * the encryption result as a [SealedData] object containing the ciphertext, nonce, and authentication tag.
   */
  @Deprecated(message = "use #seal instead")
  @Throws(Exception::class)
  fun sealNoMetadata(
    unsealedData: ByteString,
    key: SymmetricKey,
  ): SealedData

  /**
   * Decrypts data that was encrypted without metadata using XChaCha20-Poly1305.
   *
   * This method can decrypt data created by [sealNoMetadata] and provides backward compatibility
   * with legacy AES-GCM encrypted data (identified by nonce size != 24 bytes).
   */
  @Throws(Exception::class)
  fun unsealNoMetadata(
    sealedData: SealedData,
    key: SymmetricKey,
  ): ByteString

  /**
   * Encrypts data with metadata and associated data (AAD) using XChaCha20-Poly1305.
   *
   * This is the preferred encryption method as it:
   * - Includes structured metadata in the result
   * - Supports Associated Authenticated Data (AAD) for additional context authentication
   * - Returns an opaque [XCiphertext] that encodes all necessary information
   */
  @Throws(Exception::class)
  fun seal(
    unsealedData: ByteString,
    key: SymmetricKey,
    aad: ByteString,
  ): XCiphertext

  /**
   * Decrypts data that was encrypted with metadata and associated data using XChaCha20-Poly1305.
   *
   * This method decrypts [XCiphertext] created by [seal] and verifies both the ciphertext
   * integrity and the associated authenticated data (AAD). The AAD must match exactly
   * what was used during encryption or decryption will fail.
   */
  @Throws(Exception::class)
  fun unseal(
    ciphertext: XCiphertext,
    key: SymmetricKey,
    aad: ByteString,
  ): ByteString
}
