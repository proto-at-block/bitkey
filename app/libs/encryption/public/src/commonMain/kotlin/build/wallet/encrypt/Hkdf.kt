package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey
import okio.ByteString

/**
 * A Hashed Message Authentication Code (HMAC)-based key derivation function (HKDF)
 * that takes initial keying material and derives a cryptographically strong key.
 * The salt and info are optional.
 */
interface Hkdf {
  fun deriveKey(
    ikm: ByteString,
    salt: ByteString?,
    info: ByteString?,
    outputLength: Int,
  ): SymmetricKey
}
