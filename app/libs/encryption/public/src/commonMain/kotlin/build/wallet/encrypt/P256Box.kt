package build.wallet.encrypt

import dev.zacsweers.redacted.annotations.Redacted
import okio.ByteString

/**
 * This interface provides a Kotlin wrapper around a Rust implementation of the p256 box,
 * facilitating the secure exchange of encrypted messages between two parties.
 *
 * Usage involves generating a key pair with `generateKeyPair`, encrypting a message with `encrypt`,
 * and decrypting a message with `decrypt`.
 */
interface P256Box {
  /**
   * Generates a key pair for the p256 box.
   *
   * @return The key pair.
   */
  fun generateKeyPair(): P256BoxKeyPair

  /**
   * Returns a key pair instantiated from the provided secret bytes
   *
   * @param secretBytes The secret bytes to use for the key pair.
   * @throws Error If there's an issue creating the key pair.
   */
  @Throws(Error::class)
  fun keypairFromSecretBytes(secretBytes: ByteString): P256BoxKeyPair

  /**
   * Encrypts a plaintext message using the p256 box.
   *
   * @param theirPublicKey The public key of the recipient.
   * @param myPrivateKey The private key of the sender.
   * @param plaintext The plaintext message to encrypt.
   * @param nonce The nonce to use for encryption.
   * @return The encrypted message.
   * @throws Error If there's an issue encrypting the message.
   */
  @Throws(Error::class)
  fun encrypt(
    theirPublicKey: P256BoxPublicKey,
    myPrivateKey: P256BoxPrivateKey,
    nonce: XNonce,
    plaintext: ByteString,
  ): XCiphertext

  /**
   * Decrypts a sealed message using the p256 box.
   *
   * @param theirPublicKey The public key of the sender.
   * @param myPrivateKey The private key of the recipient.
   * @param sealedData The sealed message to decrypt.
   * @return The decrypted message.
   * @throws Error If there's an issue decrypting the message.
   */
  @Throws(Error::class)
  fun decrypt(
    theirPublicKey: P256BoxPublicKey,
    myPrivateKey: P256BoxPrivateKey,
    sealedData: XCiphertext,
  ): ByteString

  companion object {
    const val ALGORITHM = "P256Box"
  }
}

data class P256BoxKeyPair(
  val privateKey: P256BoxPrivateKey,
  val publicKey: P256BoxPublicKey,
)

@Redacted
data class P256BoxPrivateKey(
  val bytes: ByteString,
)

data class P256BoxPublicKey(
  val bytes: ByteString,
)

sealed class P256BoxError : Error() {
  data object InvalidAlgorithm : P256BoxError()
}
