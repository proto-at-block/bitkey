package build.wallet.firmware

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.rust.firmware.Attestation as AttestationCore

@BitkeyInject(AppScope::class)
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
