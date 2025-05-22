package bitkey.securitycenter

import build.wallet.database.SecurityInteractionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class SecurityActionsServiceFake(
  val actions: MutableList<SecurityAction> = mutableListOf(),
  val recommendations: MutableList<SecurityActionRecommendation> = actions.map {
    it.getRecommendations()
  }.flatten().toMutableList(),
  var statuses: List<SecurityRecommendationWithStatus> = emptyList(),
) : SecurityActionsService {
  /**
   * Returns a list of security actions for the given category.
   *
   * @param category the category of the action.
   */
  override suspend fun getActions(category: SecurityActionCategory): List<SecurityAction> {
    return actions
  }

  /**
   * Returns a list of recommended security actions for the customer.
   *
   */

  override fun getRecommendations(): Flow<List<SecurityActionRecommendation>> {
    return flowOf(recommendations)
  }

  override fun getRecommendationsWithInteractionStatus(): Flow<List<SecurityRecommendationWithStatus>> {
    return flowOf(statuses)
  }

  override fun hasRecommendationsRequiringAttention(): Flow<Boolean> =
    getRecommendationsWithInteractionStatus().map { list ->
      list.any { it.interactionStatus == SecurityInteractionStatus.NEW }
    }

  override suspend fun recordUserInteractionWithRecommendation(
    id: SecurityActionRecommendation,
    status: SecurityInteractionStatus,
    interactedAt: Instant,
  ): Result<Unit> {
    return Result.success(Unit)
  }

  fun clear() {
    actions.clear()
    recommendations.clear()
    statuses = emptyList()
  }

  override suspend fun markAllRecommendationsViewed() {
    val now = kotlinx.datetime.Clock.System.now()
    statuses = statuses.map {
      if (it.interactionStatus != SecurityInteractionStatus.VIEWED) {
        it.copy(
          interactionStatus = SecurityInteractionStatus.VIEWED,
          lastInteractedAt = now,
          recordUpdatedAt = now
        )
      } else {
        it
      }
    }
  }
}
