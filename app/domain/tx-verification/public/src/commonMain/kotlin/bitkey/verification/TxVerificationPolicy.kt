package bitkey.verification

import bitkey.privilegedactions.PrivilegedActionInstance

/**
 * A policy entry for setting the user's transaction verification limit.
 *
 * This represents the details of a particular request for verification.
 * There may be multiple records at a time, due to the authorization
 * delay/notify process. These details can be used to differentiate two
 * requests and contain the tokens needed to modify them.
 */
sealed interface TxVerificationPolicy {
  /**
   * A policy that has been activated by the server.
   */
  data class Active(
    val threshold: VerificationThreshold,
  ) : TxVerificationPolicy

  /**
   * A policy that still requires completion after auth is complete.
   */
  data class Pending(
    val authorization: PrivilegedActionInstance,
  ) : TxVerificationPolicy

  data object Disabled : TxVerificationPolicy
}
