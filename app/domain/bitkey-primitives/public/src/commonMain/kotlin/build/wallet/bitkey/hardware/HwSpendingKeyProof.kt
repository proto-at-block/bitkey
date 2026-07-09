package build.wallet.bitkey.hardware

/**
 * Hardware-attested proof of a derived spending key.
 *
 * - [signature] is the device-identity-key ECDSA signature
 *   returned by firmware over the derived spending pubkey (see firmware
 *   "HWV1" attestation).
 * - [certChain] is the device cert chain as attestation certificates,
 *   ordered leaf → root (typically `[identityCert, batchCert]`).
 */
data class HwSpendingKeyProof(
  val signature: HwSpendingKeyAttestationSignature,
  val certChain: List<HwAttestationCertificate>,
) {
  /**
   * `true` when this proof has both a non-empty signature and at least one cert,
   * i.e. is worth sending to f8e. Empty proofs can arise from a corrupt-row
   * fallback in the persistence adapter; treat them as equivalent to "no proof".
   */
  val isUsable: Boolean get() = signature.value.isNotEmpty() && certChain.isNotEmpty()
}

/** Returns `null` if this proof is empty (see [HwSpendingKeyProof.isUsable]). */
fun HwSpendingKeyProof?.usableOrNull(): HwSpendingKeyProof? = this?.takeIf { it.isUsable }
