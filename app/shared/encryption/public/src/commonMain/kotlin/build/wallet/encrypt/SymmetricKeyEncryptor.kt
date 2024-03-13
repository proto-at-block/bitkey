package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey
import okio.ByteString

/**
 * Encrypts and decrypts data via XChaCha20-Poly1305.
 */
interface SymmetricKeyEncryptor {
  @Throws(Exception::class)
  fun seal(
    unsealedData: ByteString,
    key: SymmetricKey,
  ): SealedData

  @Throws(Exception::class)
  fun unseal(
    sealedData: SealedData,
    key: SymmetricKey,
  ): ByteString
}
