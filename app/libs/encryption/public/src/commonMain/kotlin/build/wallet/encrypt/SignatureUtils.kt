package build.wallet.encrypt

import okio.ByteString

/**
 * Utility interface for encoding ECDSA signatures to DER format.
 */
interface SignatureUtils {
  /**
   * Encodes an ECDSA signature to DER format.
   *
   * @param compactSignature The compact signature (64 bytes: r + s components)
   * @return The DER-encoded signature
   * @throws IllegalArgumentException if the signature is invalid
   */
  @Throws(IllegalArgumentException::class)
  fun encodeSignatureToDer(compactSignature: ByteArray): ByteString

  /**
   * Decodes a DER-encoded signature to compact format.
   *
   * @param derSignature The DER-encoded signature
   * @return The compact signature (64 bytes: r + s components)
   * @throws IllegalArgumentException if the signature is invalid
   */
  @Throws(IllegalArgumentException::class)
  fun decodeSignatureFromDer(derSignature: ByteString): ByteArray
}
