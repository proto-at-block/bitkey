package build.wallet.firmware

import build.wallet.core.Attestation as AttestationCore

class HardwareAttestationImpl : HardwareAttestation {
  override fun verifyCertChain(
    identityCert: List<UByte>,
    batchCert: List<UByte>,
  ): String {
    return AttestationCore().verifyDeviceIdentityCertChain(
      identityCertDer = identityCert,
      batchCertDer = batchCert
    )
  }

  override fun generateChallenge(): List<UByte> {
    return AttestationCore().generateChallenge()
  }
}
