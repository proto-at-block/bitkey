package build.wallet.encrypt

/**
 * A generator for 24-byte random nonces to be used with XChaCha20.
 */
interface XNonceGenerator {
  @Throws(XNonceGeneratorError::class)
  fun generateXNonce(): XNonce
}

sealed class XNonceGeneratorError(override val message: String?) : Error(message) {
  data class XNonceGenerationError(
    override val message: String?,
  ) : XNonceGeneratorError(message)
}
