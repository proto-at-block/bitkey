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
data class TxVerificationPolicy(
  val id: Id,
  val threshold: VerificationThreshold,
  val authorization: DelayNotifyAuthorization? = null,
) {
  @JvmInline
  value class Id(
    val value: String,
  )

  /**
   * Pending Auth request token for a verification policy.
   */
  @Redacted
  data class DelayNotifyAuthorization(
    @Unredacted
    val delayEndTime: Instant,
    val cancellationToken: String,
    val completionToken: String,
  )
}
