package build.wallet.crypto

import okio.ByteString

enum class Spake2Role {
  Alice,
  Bob,
}

data class Spake2Keys(
  val aliceEncryptionKey: ByteString,
  val bobEncryptionKey: ByteString,
  val aliceConfKey: ByteString,
  val bobConfKey: ByteString,
)

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
   * @param password The shared password used for key exchange, as a ByteString.
   * @return A ByteString containing the SPAKE2 message to be sent to the other party.
   * @throws Error If there's an issue generating the message.
   */
  @Throws(Error::class)
  fun generateMsg(password: ByteString): ByteString

  /**
   * Processes the SPAKE2 message received from the other party and derives the shared keys.
   *
   * @param theirMsg The SPAKE2 message received from the other party, as a ByteString.
   * @param aad Optional additional authenticated data to bind into the protocol, as a ByteString.
   * @return A Spake2Keys object containing the derived encryption and confirmation keys.
   * @throws Error If there's an issue processing the message or deriving keys.
   */
  @Throws(Error::class)
  fun processMsg(
    theirMsg: ByteString,
    aad: ByteString?,
  ): Spake2Keys

  /**
   * Generates a key confirmation message using the derived keys.
   *
   * @param keys The Spake2Keys object containing the derived keys.
   * @return A ByteString containing the key confirmation message to be sent to the other party.
   * @throws Error If there's an issue generating the key confirmation message.
   */
  @Throws(Error::class)
  fun generateKeyConfMsg(keys: Spake2Keys): ByteString

  /**
   * Processes the key confirmation message received from the other party.
   *
   * @param receivedMac The key confirmation message received from the other party, as a ByteString.
   * @param keys The Spake2Keys object containing the derived keys.
   * @throws Error If there's an issue verifying the key confirmation message.
   */
  @Throws(Error::class)
  fun processKeyConfMsg(
    receivedMac: ByteString,
    keys: Spake2Keys,
  )
}
