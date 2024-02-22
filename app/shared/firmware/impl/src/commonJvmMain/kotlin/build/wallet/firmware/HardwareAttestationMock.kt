package build.wallet.firmware

class HardwareAttestationMock : HardwareAttestation {
  override fun verifyCertChain(
    identityCert: List<UByte>,
    batchCert: List<UByte>,
  ): String = "mock"

  override fun generateChallenge(): List<UByte> = emptyList()
}
