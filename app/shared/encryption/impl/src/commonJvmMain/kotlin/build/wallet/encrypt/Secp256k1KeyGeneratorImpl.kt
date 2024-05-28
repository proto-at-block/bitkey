package build.wallet.encrypt

import build.wallet.toUByteList
import okio.ByteString.Companion.toByteString
import java.security.SecureRandom
import build.wallet.rust.core.SecretKey as CoreSecretKey

class Secp256k1KeyGeneratorImpl : Secp256k1KeyGenerator {
  /**
   * This method initializes a `CoreSecretKey` exposed by `coreFFI` with the underlying byte array
   * representation of `SecretKey`.
   *
   * We then call `asPublic()`, which is calculates the corresponding public key for a secret key.
   * The result is a `PublicKey` instance that wraps the underlying string value of the public
   * key.
   *
   * This method will be useful for deriving public keys from any secret key (config, auth, or
   * spend).
   */
  override fun derivePublicKey(privateKey: Secp256k1PrivateKey): Secp256k1PublicKey {
    val coreSecretKey = CoreSecretKey(privateKey.bytes.toUByteList())
    return Secp256k1PublicKey(coreSecretKey.asPublic())
  }

  override fun generatePrivateKey(): Secp256k1PrivateKey {
    val randomBytes = ByteArray(32)
    SecureRandom().nextBytes(randomBytes)
    // Check whether the private key is valid by passing it to the Core constructor
    CoreSecretKey(randomBytes.toUByteList())
    return Secp256k1PrivateKey(randomBytes.toByteString())
  }

  override fun generateKeypair(): Secp256k1Keypair {
    val privateKey = generatePrivateKey()
    val publicKey = derivePublicKey(privateKey)
    return Secp256k1Keypair(publicKey, privateKey)
  }
}
