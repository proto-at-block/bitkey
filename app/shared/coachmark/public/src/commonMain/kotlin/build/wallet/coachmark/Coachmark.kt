package build.wallet.coachmark

import kotlinx.datetime.Instant

/**
 * Coachmark data class.
 *
 * @param coachmarkId The coachmark identifier.
 * @param viewed Whether the coachmark has been viewed.
 * @param expiration The expiration date of the coachmark.
 */
data class Coachmark(
  val coachmarkId: String,
  val viewed: Boolean,
  val expiration: Instant,
)
