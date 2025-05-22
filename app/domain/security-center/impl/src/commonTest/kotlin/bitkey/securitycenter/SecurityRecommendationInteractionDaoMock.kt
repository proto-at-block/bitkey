package bitkey.securitycenter

import build.wallet.database.SecurityInteractionStatus
import build.wallet.database.sqldelight.SecurityRecommendationInteractionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant

class SecurityRecommendationInteractionDaoMock : SecurityRecommendationInteractionDao {
  private val interactions = mutableMapOf<SecurityActionRecommendation, SecurityRecommendationInteractionEntity>()

  override suspend fun recordRecommendationActive(
    id: SecurityActionRecommendation,
    triggeredAt: Instant,
    currentTime: Instant,
  ) {
    val entity = SecurityRecommendationInteractionEntity(
      recommendationId = id.name,
      recordUpdatedAt = currentTime,
      lastInteractedAt = null,
      interactionStatus = SecurityInteractionStatus.NEW,
      lastRecommendationTriggeredAt = triggeredAt
    )
    interactions[id] = entity
  }

  override suspend fun recordUserInteraction(
    id: SecurityActionRecommendation,
    status: SecurityInteractionStatus,
    interactedAt: Instant,
    currentTime: Instant,
  ) {
    interactions[id]?.let {
      interactions[id] = it.copy(
        interactionStatus = status,
        lastInteractedAt = interactedAt,
        recordUpdatedAt = currentTime
      )
    }
  }

  override fun getInteraction(
    id: SecurityActionRecommendation,
  ): Flow<SecurityRecommendationInteractionEntity> {
    val entity = interactions[id]
    requireNotNull(entity) { "Interaction not found for id: $id" }
    return flowOf(entity)
  }

  override fun getInteractions(
    ids: Set<SecurityActionRecommendation>,
  ): Flow<Map<SecurityActionRecommendation, SecurityRecommendationInteractionEntity>> {
    val selectedInteractions = interactions.filterKeys { it in ids }
    return flowOf(selectedInteractions)
  }

  override fun getAllInteractions(): Flow<List<SecurityRecommendationInteractionEntity>> {
    return flowOf(interactions.values.toList())
  }

  override suspend fun resetRecommendationStatusToNew(
    id: SecurityActionRecommendation,
    newTriggeredAt: Instant,
    currentTime: Instant,
  ) {
    interactions[id]?.let {
      interactions[id] = it.copy(
        interactionStatus = SecurityInteractionStatus.NEW,
        recordUpdatedAt = currentTime,
        lastRecommendationTriggeredAt = newTriggeredAt
      )
    }
  }

  override suspend fun deleteRecommendation(id: SecurityActionRecommendation) {
    interactions.remove(id)
  }
}
