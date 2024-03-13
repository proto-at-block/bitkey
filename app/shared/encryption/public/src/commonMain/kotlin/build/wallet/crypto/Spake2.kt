package build.wallet.crypto

import dev.zacsweers.redacted.annotations.Redacted
import okio.ByteString
import okio.ByteString.Companion.decodeHex

enum class Spake2Role {
  Alice,
  Bob,
}

data class Spake2SymmetricKeys(
  val aliceEncryptionKey: ByteString,
  val bobEncryptionKey: ByteString,
  val aliceConfKey: ByteString,
  val bobConfKey: ByteString,
)

data class Spake2Params(
  val role: Spake2Role,
  val myName: String,
  val theirName: String,
  @Redacted
  val password: ByteString,
)

data class Spake2KeyPair(
  val privateKey: Spake2PrivateKey,
  val publicKey: Spake2PublicKey,
)

@Redacted
data class Spake2PrivateKey(
  val bytes: ByteString,
)

data class Spake2PublicKey(
  val bytes: ByteString,
)

fun Spake2PublicKey.toPublicKey(): PublicKey {
  val publicKeyString = this.bytes.hex()
  return PublicKey(publicKeyString)
}

fun Spake2PrivateKey.toPrivateKey(): PrivateKey {
  val privateKeyBytes = this.bytes
  return PrivateKey(privateKeyBytes)
}

fun PublicKey.toSpake2PublicKey(): Spake2PublicKey {
  val byteString = this.value.decodeHex()

  return Spake2PublicKey(byteString)
}

fun PrivateKey.toSpake2PrivateKey(): Spake2PrivateKey {
  val byteString = this.bytes

  return Spake2PrivateKey(byteString)
}

/**
 * This interface provides a Kotlin wrapper around a Rust implementation of the SPAKE2 protocol, facilitating
 * the secure exchange of encryption keys derived from a shared password, without transmitting the password itself
 * or its direct cryptographic derivatives over the network. It supports two roles, Alice and Bob, as part of the
 * mutual authentication process.
 *
 * Usage involves generating a message with `generateMsg`, processing the counterpart's message with `processMsg`,
 * and then optionally generating and verifying key confirmation messages to ensure both parties have derived
 * the same keys.
 */
interface Spake2 {
  /**
   * Generates an initial SPAKE2 message based on the provided password.
   *
   * @param spake2Params The parameters for the SPAKE2 session.
   * @return A key pair. The public key is sent to the other party.
   * @throws Error If there's an issue generating the message.
   */
  @Throws(Error::class)
  fun generateKeyPair(spake2Params: Spake2Params): Spake2KeyPair

  /**
   * Processes the SPAKE2 message received from the other party and derives the shared keys.
   *
   * @param spake2Params The parameters for the SPAKE2 session.
   * @param myKeyPair The SPAKE2 key pair of the caller.
   * @param theirPublicKey The SPAKE2 public key received from the other party.
   * @param aad Optional additional authenticated data to bind into the protocol, as a ByteString.
   * @return A Spake2SymmetricKeys object containing the derived encryption and confirmation keys.
   * @throws Error If there's an issue processing the message or deriving keys.
   */
  @Throws(Error::class)
  fun processTheirPublicKey(
    spake2Params: Spake2Params,
    myKeyPair: Spake2KeyPair,
    theirPublicKey: Spake2PublicKey,
    aad: ByteString?,
  ): Spake2SymmetricKeys

  /**
   * Generates a key confirmation message using the derived keys.
   *
   * @param role The role of the caller in the SPAKE2 session.
   * @param keys The Spake2SymmetricKeys object containing the derived keys.
   * @return A ByteString containing the key confirmation message to be sent to the other party.
   * @throws Error If there's an issue generating the key confirmation message.
   */
  @Throws(Error::class)
  fun generateKeyConfMsg(
    role: Spake2Role,
    keys: Spake2SymmetricKeys,
  ): ByteString

  /**
   * Processes the key confirmation message received from the other party.
   *
   * @param role The role of the caller in the SPAKE2 session.
   * @param receivedKeyConfMsg The key confirmation message received from the other party, as a ByteString.
   * @param keys The Spake2SymmetricKeys object containing the derived keys.
   * @throws Error If there's an issue verifying the key confirmation message.
   */
  @Throws(Error::class)
  fun processKeyConfMsg(
    role: Spake2Role,
    receivedKeyConfMsg: ByteString,
    keys: Spake2SymmetricKeys,
  )
}
