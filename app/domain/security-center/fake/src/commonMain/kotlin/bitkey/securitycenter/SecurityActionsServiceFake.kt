package bitkey.securitycenter

import build.wallet.database.SecurityInteractionStatus
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant

class SecurityActionsServiceFake(
  val actions: MutableList<SecurityAction> = mutableListOf(),
  val recommendations: MutableList<SecurityActionRecommendation> = actions.map {
    it.getRecommendations()
  }.flatten().toMutableList(),
  var statuses: List<SecurityRecommendationWithStatus> = emptyList(),
) : SecurityActionsService {
  override val securityActionsWithRecommendations: StateFlow<SecurityActionsWithRecommendations> = MutableStateFlow(
    SecurityActionsWithRecommendations(
      securityActions = actions.filter { it.category() == SecurityActionCategory.SECURITY },
      recoveryActions = actions.filter { it.category() == SecurityActionCategory.RECOVERY },
      recommendations = recommendations
    )
  )

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
