package build.wallet.crypto

enum class WsmIntegrityKeyVariant(val pubkey: String) {
  Test("03078451e0c1e12743d2fdd93ae7d03d5cf7813d2f612de10904e1c6a0b87f7071"),
  Prod("0295216a2e0b54b382cc3938e207298d21cb8c5f686f78b05d9f14b4e4669e560f"),
}

/**
 * A workaround hack to fix a Kotlin - ObjC interop issue:
 * "Throwing method cannot be an implementation of an @objc requirement because it returns a
 * value of type 'Bool'; return 'Void' or a type that bridges to an Objective-C class"
 *
 * TODO(BKR-1022): remove this workaround and return Boolean straight up.
 */
data class WsmVerifierResult(
  val isValid: Boolean,
)

interface WsmVerifier {
  /**
   * Verifies a message from WSM. The message must have been signed with the WSM Integrity Key.
   *
   * @param base58Message The base58-check encoded message to verify.
   * @param signature The hex-encoded signature to verify against.
   * @param keyVariant The variant of the WSM Integrity Key to use. Staging and development both use 'test'.
   * @return True if the signature is valid for the message, false otherwise.
   * @throws Error If there is an invalid public key or signature.
   */
  @Throws(Error::class)
  fun verify(
    base58Message: String,
    signature: String,
    keyVariant: WsmIntegrityKeyVariant,
  ): WsmVerifierResult
}
