package bitkey.verification

/**
 * Finite states for a verification operation.
 */
sealed interface TxVerificationState {
  /**
   * Verification state when awaiting the user to complete verification server-side.
   */
  data object Pending : TxVerificationState

  /**
   * User has successfully completed verification on the server and hardware grant is available.
   */
  data class Success(
    val hardwareGrant: TxVerificationApproval,
  ) : TxVerificationState

  /**
   * Verification timed-out before it could be completed.
   */
  data object Expired : TxVerificationState

  /**
   * Verification is marked as a unsuccessful by the server and must be re-started.
   */
  data object Failed : TxVerificationState
}

/**
 * Whether the verification is in any terminal, but not successful, state.
 */
val TxVerificationState.isUnsuccessful: Boolean get() {
  return this is TxVerificationState.Expired || this is TxVerificationState.Failed
}
