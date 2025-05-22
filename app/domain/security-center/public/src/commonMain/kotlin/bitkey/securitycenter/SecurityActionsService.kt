package bitkey.securitycenter

import build.wallet.database.SecurityInteractionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface SecurityActionsService {
  /**
   * Returns a list of security actions for the given category.
   *
   * @param category the category of the action.
   */
  suspend fun getActions(category: SecurityActionCategory): List<SecurityAction>

  /**
   * Returns a list of recommended security actions for the customer.
   *
   */
  fun getRecommendations(): Flow<List<SecurityActionRecommendation>>

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
