package build.wallet.firmware

enum class FirmwareCertType {
  BATCH,
  IDENTITY,
}

class AttestationException(message: String) : Exception(message)

interface HardwareAttestation {
  /**
   * Verifies the certificate chain of the device identity certificate.
   *
   * @param identityCert The device-specific identity certificate.
   * @param batchCert The batch certificate.
   * @return The device serial number.
   */
  @Throws(AttestationException::class)
  fun verifyCertChain(
    identityCert: List<UByte>,
    batchCert: List<UByte>,
  ): String

  /**
   * Generates a challenge for the device.
   *
   * @return The 16 byte challenge.
   */
  @Throws(AttestationException::class)
  fun generateChallenge(): List<UByte>
}
