package build.wallet.encrypt

interface Secp256k1KeyGenerator {
  /**
   * Derives the public key from a given [Secp256k1PrivateKey].
   */
  fun derivePublicKey(privateKey: Secp256k1PrivateKey): Secp256k1PublicKey

  @Throws(Secp256k1KeyGeneratorError::class)
  fun generatePrivateKey(): Secp256k1PrivateKey

  @Throws(Secp256k1KeyGeneratorError::class)
  fun generateKeypair(): Secp256k1Keypair
}

sealed class Secp256k1KeyGeneratorError(override val message: String?) : Error(message) {
  data class PrivateKeyGenerationError(
    override val message: String?,
  ) : Secp256k1KeyGeneratorError(message)
}
