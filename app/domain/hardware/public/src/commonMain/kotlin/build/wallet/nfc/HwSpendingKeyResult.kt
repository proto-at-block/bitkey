package build.wallet.nfc

import build.wallet.bitkey.hardware.HwAttestationCertificate
import build.wallet.bitkey.hardware.HwSpendingKeyAttestationSignature
import build.wallet.bitkey.hardware.HwSpendingKeyProof
import build.wallet.bitkey.hardware.HwSpendingPublicKey

/**
 * Result of a firmware spending-key derivation command.
 *
 * @property publicKey the derived public spending key
 * @property attestationSignature base64-encoded device-identity-key signature
 *   over the derived pubkey returned by firmware as part of "HWV1" attestation.
 *   `null` for pre-attestation firmware that does not populate the field.
 * @property certChain the device cert chain (leaf → root-ish, typically
 *   `[identityCert, batchCert]`) as base64-encoded DER certificates needed to
 *   validate [attestationSignature]. Empty when [attestationSignature] is
 *   `null` or the certs couldn't be fetched in the same NFC session.
 *
 * Stored as base64 strings so the data class can use the default structural
 * [equals]/[hashCode].
 */
data class HwSpendingKeyResult(
  val publicKey: HwSpendingPublicKey,
  val attestationSignature: String?,
  val certChain: List<String> = emptyList(),
)

/**
 * Convert the NFC spending-key result into a wire-ready attestation proof.
 *
 * Returns `null` when firmware did not attest the key (`attestationSignature`
 * absent or empty) or the cert chain is empty — the server's enrollment gate
 * decides whether that's acceptable.
 */
fun HwSpendingKeyResult.toSpendingKeyProof(): HwSpendingKeyProof? {
  val signature = attestationSignature ?: return null
  if (signature.isEmpty() || certChain.isEmpty()) return null
  return HwSpendingKeyProof(
    signature = HwSpendingKeyAttestationSignature(signature),
    certChain = certChain.map(::HwAttestationCertificate)
  )
}
