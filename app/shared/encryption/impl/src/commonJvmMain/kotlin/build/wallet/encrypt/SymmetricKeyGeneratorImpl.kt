package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey
import build.wallet.crypto.SymmetricKeyImpl
import okio.ByteString.Companion.toByteString
import javax.crypto.KeyGenerator

/**
 * Android implementation of [SymmetricKeyGenerator] which generates a AES 256-bit symmetric key.
 */
class SymmetricKeyGeneratorImpl : SymmetricKeyGenerator {
  override fun generate(): SymmetricKey {
    val keyGenerator =
      KeyGenerator.getInstance(SymmetricKeyImpl.ALGORITHM).apply {
        init(256)
      }
    return SymmetricKeyImpl(raw = keyGenerator.generateKey().encoded.toByteString())
  }
}
