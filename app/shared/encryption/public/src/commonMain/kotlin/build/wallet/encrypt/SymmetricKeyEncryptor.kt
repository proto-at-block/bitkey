package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey
import okio.ByteString

/**
 * Encrypts and decrypts data via XChaCha20-Poly1305.
 */
interface SymmetricKeyEncryptor {
  fun seal(
    unsealedData: ByteString,
    key: SymmetricKey,
  ): SealedData

  fun unseal(
    sealedData: SealedData,
    key: SymmetricKey,
  ): ByteString
}
