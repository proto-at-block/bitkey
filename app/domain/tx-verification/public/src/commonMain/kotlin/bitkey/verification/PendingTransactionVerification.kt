package bitkey.verification

import kotlinx.datetime.Instant

/**
 * Represents the data required to track a verification operation in-progress.
 */
data class PendingTransactionVerification(
  val id: TxVerificationId,
  val expiration: Instant,
)
