package build.wallet.f8e.auth

/**
 * Sealed type representing the proof required to authorize a privileged f8e operation.
 * Either a hardware factor proof-of-possession or a hardware-signed action proof.
 * These are mutually exclusive — a request never carries both.
 */
sealed interface PrivilegedActionProof {
  data class HwKeyProof(
    val hwFactorProofOfPossession: HwFactorProofOfPossession,
  ) : PrivilegedActionProof

  data class HwSignedAction(
    val actionProof: ActionProof,
  ) : PrivilegedActionProof

  data class AppSignedAction(
    val actionProof: ActionProof,
  ) : PrivilegedActionProof
}
