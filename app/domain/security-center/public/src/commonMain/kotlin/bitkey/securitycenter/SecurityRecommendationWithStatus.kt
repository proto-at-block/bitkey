package bitkey.securitycenter

import build.wallet.database.SecurityInteractionStatus
import kotlinx.datetime.Instant

data class SecurityRecommendationWithStatus(
  val recommendation: SecurityActionRecommendation,
  val interactionStatus: SecurityInteractionStatus,
  val lastRecommendationTriggeredAt: Instant,
  val lastInteractedAt: Instant?,
  val recordUpdatedAt: Instant,
)
