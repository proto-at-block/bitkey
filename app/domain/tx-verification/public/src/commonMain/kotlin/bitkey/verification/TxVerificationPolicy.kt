package bitkey.verification

import dev.zacsweers.redacted.annotations.Redacted
import dev.zacsweers.redacted.annotations.Unredacted
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline

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
   * Local identifier used to track policy records.
   */
  val id: PolicyId

  /**
   * The max limit for send amounts that will require transaction verification.
   */
  val threshold: VerificationThreshold

  /**
   * A policy that has been activated by the server.
   */
  data class Active(
    override val id: PolicyId,
    override val threshold: VerificationThreshold,
  ) : TxVerificationPolicy

  /**
   * A policy that still requires completion after auth is complete.
   */
  data class Pending(
    override val id: PolicyId,
    override val threshold: VerificationThreshold,
    val authorization: DelayNotifyAuthorization,
  ) : TxVerificationPolicy

  /**
   * Local identifier used to track policy records.
   */
  @JvmInline
  value class PolicyId(
    val value: Long,
  )

  /**
   * Pending Auth request token for a verification policy.
   */
  @Redacted
  data class DelayNotifyAuthorization(
    @Unredacted
    val id: AuthId,
    @Unredacted
    val delayEndTime: Instant,
    val cancellationToken: String,
    val completionToken: String,
  ) {
    @JvmInline
    value class AuthId(
      val value: String,
    )
  }
}
