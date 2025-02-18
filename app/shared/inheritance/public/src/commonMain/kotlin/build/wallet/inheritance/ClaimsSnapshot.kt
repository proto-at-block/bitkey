package build.wallet.inheritance

import build.wallet.bitkey.inheritance.InheritanceClaims
import kotlinx.datetime.Instant

/**
 * Associates a set of claims with the timestamp they were retrieved.
 *
 * This is used to emit a snapshot of a claim state at a point in time so
 * that the approval state of a claim may be calculated statically, rather
 * than by polling the clock.
 */
data class ClaimsSnapshot(
  val timestamp: Instant,
  val claims: InheritanceClaims,
)
