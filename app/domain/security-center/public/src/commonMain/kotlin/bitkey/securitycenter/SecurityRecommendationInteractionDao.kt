package bitkey.securitycenter

import build.wallet.database.SecurityInteractionStatus
import build.wallet.database.sqldelight.SecurityRecommendationInteractionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface SecurityRecommendationInteractionDao {
  /**
   * Records that a recommendation is currently active or has been re-triggered by the system.
   *
   * If the recommendation does not exist in the database, it will be inserted with a status of NEW.
   * If it already exists, its lastRecommendationTriggeredAt and recordUpdatedAt timestamps will be updated.
   * The interactionStatus will NOT be changed if it already exists (e.g., from VIEWED back to NEW here).
   * Logic for resetting status to NEW based on re-triggering should be handled by the calling service if needed.
   */
  suspend fun recordRecommendationActive(
    id: SecurityActionRecommendation,
    triggeredAt: Instant,
    currentTime: Instant,
  )

  /**
   * Records a user's interaction with a security recommendation.
   *
   * This typically means the user has viewed the recommendation.
   * Updates the interactionStatus, lastInteractedAt, and recordUpdatedAt timestamps.
   */
  suspend fun recordUserInteraction(
    id: SecurityActionRecommendation,
    status: SecurityInteractionStatus,
    interactedAt: Instant,
    currentTime: Instant,
  )

  /**
   * Retrieves the interaction details for a specific recommendation.
   */
  fun getInteraction(
    id: SecurityActionRecommendation,
  ): Flow<SecurityRecommendationInteractionEntity>

  /**
   * Retrieves the interaction details for a set of recommendations.
   * Returns a Flow of a Map where the key is the recommendation ID and the value is the entity.
   */
  fun getInteractions(
    ids: Set<SecurityActionRecommendation>,
  ): Flow<Map<SecurityActionRecommendation, SecurityRecommendationInteractionEntity>>

  /**
   * Retrieves all recorded recommendation interactions.
   */
  fun getAllInteractions(): Flow<List<SecurityRecommendationInteractionEntity>>

  /**
   * Sets the interaction status of a specific recommendation back to NEW.
   * This might be used if a previously VIEWED item is re-triggered and needs to re-appear as new for badging.
   */
  suspend fun resetRecommendationStatusToNew(
    id: SecurityActionRecommendation,
    newTriggeredAt: Instant,
    currentTime: Instant,
  )

  /**
   * Deletes the persisted recommendation interaction.
   */
  suspend fun deleteRecommendation(id: String)

  /**
   * Deletes all persisted recommendation interactions. Used for the [AppDataDeleter]
   */
  suspend fun clear()
}
