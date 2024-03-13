package build.wallet.encrypt

import dev.zacsweers.redacted.annotations.Redacted
import okio.ByteString

/**
 * This interface provides a Kotlin wrapper around a Rust implementation of the crypto box,
 * facilitating the secure exchange of encrypted messages between two parties.
 *
 * Usage involves generating a key pair with `generateKeyPair`, encrypting a message with `encrypt`,
 * and decrypting a message with `decrypt`.
 */
interface CryptoBox {
  /**
   * Generates a key pair for the crypto box.
   *
   * @return The key pair.
   */
  fun generateKeyPair(): CryptoBoxKeyPair

  /**
   * Encrypts a plaintext message using the crypto box.
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
    theirPublicKey: CryptoBoxPublicKey,
    myPrivateKey: CryptoBoxPrivateKey,
    nonce: XNonce,
    plaintext: ByteString,
  ): XCiphertext

  /**
   * Decrypts a sealed message using the crypto box.
   *
   * @param theirPublicKey The public key of the sender.
   * @param myPrivateKey The private key of the recipient.
   * @param sealedData The sealed message to decrypt.
   * @return The decrypted message.
   * @throws Error If there's an issue decrypting the message.
   */
  @Throws(Error::class)
  fun decrypt(
    theirPublicKey: CryptoBoxPublicKey,
    myPrivateKey: CryptoBoxPrivateKey,
    sealedData: XCiphertext,
  ): ByteString

  companion object {
    const val ALGORITHM = "CryptoBox"
  }
}

data class CryptoBoxKeyPair(
  val privateKey: CryptoBoxPrivateKey,
  val publicKey: CryptoBoxPublicKey,
)

@Redacted
data class CryptoBoxPrivateKey(
  val bytes: ByteString,
)

data class CryptoBoxPublicKey(
  val bytes: ByteString,
)

sealed class CryptoBoxError : Error() {
  data object InvalidAlgorithm : CryptoBoxError()
}
