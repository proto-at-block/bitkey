package bitkey.securitycenter

import build.wallet.database.SecurityInteractionStatus
import build.wallet.worker.AppWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant

interface SecurityActionsService {
  val securityActionsWithRecommendations: StateFlow<SecurityActionsWithRecommendations>

  fun getRecommendationsWithInteractionStatus(): Flow<List<SecurityRecommendationWithStatus>>

  suspend fun recordUserInteractionWithRecommendation(
    id: SecurityActionRecommendation,
    status: SecurityInteractionStatus,
    interactedAt: Instant,
  ): Result<Unit>

  fun hasRecommendationsRequiringAttention(): Flow<Boolean>

  /**
   * Marks all current recommendations as viewed
   */
  suspend fun markAllRecommendationsViewed()
}

/**
 * Worker that fetches security actions and recommendations.
 * This worker is used to update the security actions in the background.
 */
interface SecurityActionsWorker : AppWorker

/**
 * Data class representing security actions filtered by category along with their recommendations.
 *
 * @param securityActions List of actions that are related to security.
 * @param recoveryActions List of actions that are related to recovery.
 * @param recommendations List of recommendations that are active.
 * @param atRiskRecommendations List of recommendations that are at risk, which should not be
 * present if there are other recommendations.
 */
data class SecurityActionsWithRecommendations(
  val securityActions: List<SecurityAction>,
  val recoveryActions: List<SecurityAction>,
  val recommendations: List<SecurityActionRecommendation>,
  val atRiskRecommendations: List<SecurityActionRecommendation> = emptyList(),
) {
  init {
    // if there are at-risk recommendations, there should be no other recommendations
    if (atRiskRecommendations.isNotEmpty()) require(recommendations.isEmpty())
  }
}
