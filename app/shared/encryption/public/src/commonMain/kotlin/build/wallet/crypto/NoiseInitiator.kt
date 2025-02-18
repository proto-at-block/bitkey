package build.wallet.crypto

/**
 * Selects which static Noise public key to use: test (for development, staging)
 * or prod (for production).
 */
enum class NoiseKeyVariant(
  val serverStaticPubkey: String,
) {
  // These are base64 because Apple Data APIs don't like hex. The hex keys are shown below:
  // 046d0f2d82024c8a9defa34ac4a82f659247b38e0fdf3024d579d981f9ed7a8661f8efe8bd86dc1ba05fc986f1c9f12e450edcb1c34d072c7cde13a897767050ab
  Test("BG0PLYICTIqd76NKxKgvZZJHs44P3zAk1XnZgfnteoZh+O/ovYbcG6BfyYbxyfEuRQ7cscNNByx83hOol3ZwUKs="),

  // TODO: Replace prod with real prod keys.
  // 046d0f2d82024c8a9defa34ac4a82f659247b38e0fdf3024d579d981f9ed7a8661f8efe8bd86dc1ba05fc986f1c9f12e450edcb1c34d072c7cde13a897767050ab
  Prod("BG0PLYICTIqd76NKxKgvZZJHs44P3zAk1XnZgfnteoZh+O/ovYbcG6BfyYbxyfEuRQ7cscNNByx83hOol3ZwUKs="),
}

data class NoiseSessionState(
  val sessionId: String,
  val keyType: NoiseKeyVariant,
)

data class InitiateHandshakeMessage(
  val payload: ByteArray,
)

/**
 * NoiseInitiator handles the Noise protocol handshake and encryption/decryption as an initiator,
 * and is responsible for generating the private key and handling the session state.
 *
 * This is the thin stateful wrapper around the Noise session with the server.
 *
 * Initiate/advance/finalize handshake methods must be called in that order.
 */
interface NoiseInitiator {
  @Throws(Exception::class)
  fun initiateHandshake(keyType: NoiseKeyVariant): InitiateHandshakeMessage

  @Throws(Exception::class)
  fun advanceHandshake(
    keyType: NoiseKeyVariant,
    peerHandshakeMessage: ByteArray,
  )

  @Throws(Exception::class)
  fun finalizeHandshake(keyType: NoiseKeyVariant)

  /**
   * Encrypts a message using Noise symmetric session keys. Can only be called after the handshake
   * is finalized.
   */
  @Throws(Exception::class)
  fun encryptMessage(
    keyType: NoiseKeyVariant,
    message: ByteArray,
  ): ByteArray

  /**
   * Decrypts a message using Noise symmetric session keys. Can only be called after the handshake
   * is finalized.
   */
  @Throws(Exception::class)
  fun decryptMessage(
    keyType: NoiseKeyVariant,
    ciphertext: ByteArray,
  ): ByteArray
}
