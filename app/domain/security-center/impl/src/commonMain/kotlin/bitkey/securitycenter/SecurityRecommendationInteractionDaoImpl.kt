package bitkey.securitycenter

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.SecurityInteractionStatus
import build.wallet.database.sqldelight.SecurityRecommendationInteractionEntity
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

@BitkeyInject(AppScope::class)
class SecurityRecommendationInteractionDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : SecurityRecommendationInteractionDao {
  private fun SecurityActionRecommendation.toDbString() = this.name

  private fun String.toSecurityActionRecommendation(): SecurityActionRecommendation =
    SecurityActionRecommendation.valueOf(this)

  private fun Set<SecurityActionRecommendation>.toDbStrings(): Collection<String> =
    this.map { it.toDbString() }.toSet()

  override suspend fun recordRecommendationActive(
    id: SecurityActionRecommendation,
    triggeredAt: Instant,
    currentTime: Instant,
  ) = withContext(Dispatchers.Default) {
    databaseProvider.database().securityRecommendationInteractionEntityQueries.insertOrUpdateActiveRecommendation(
      recommendationId = id.toDbString(),
      interactionStatus = SecurityInteractionStatus.NEW,
      lastInteractedAt = null,
      lastRecommendationTriggeredAt = triggeredAt,
      recordUpdatedAt = currentTime
    )
  }

  override suspend fun recordUserInteraction(
    id: SecurityActionRecommendation,
    status: SecurityInteractionStatus,
    interactedAt: Instant,
    currentTime: Instant,
  ) = withContext(Dispatchers.Default) {
    databaseProvider.database().securityRecommendationInteractionEntityQueries.updateUserInteraction(
      recommendationId = id.toDbString(),
      interactionStatus = status,
      lastInteractedAt = interactedAt,
      recordUpdatedAt = currentTime
    )
  }

  override fun getInteraction(
    id: SecurityActionRecommendation,
  ): Flow<SecurityRecommendationInteractionEntity> =
    flow {
      val interaction = withContext(Dispatchers.Default) {
        databaseProvider.database().securityRecommendationInteractionEntityQueries
          .getById(id.toDbString())
          .executeAsOneOrNull()
      }
      if (interaction == null) requireNotNull(interaction) { "No record found for id: $id" }
      emit(interaction)
    }

  override fun getInteractions(
    ids: Set<SecurityActionRecommendation>,
  ): Flow<Map<SecurityActionRecommendation, SecurityRecommendationInteractionEntity>> =
    flow {
      val interactions: List<SecurityRecommendationInteractionEntity> =
        databaseProvider.database().securityRecommendationInteractionEntityQueries
          .getByIds(ids.toDbStrings())
          .executeAsList()

      val interactionMap = interactions.associateBy(
        keySelector = { entity -> entity.recommendationId.toSecurityActionRecommendation() },
        valueTransform = { entity -> entity }
      )

      emit(interactionMap)
    }

  override fun getAllInteractions(): Flow<List<SecurityRecommendationInteractionEntity>> =
    flow {
      val allInteractions = withContext(Dispatchers.Default) {
        databaseProvider.database().securityRecommendationInteractionEntityQueries
          .getAll()
          .executeAsList()
      }
      emit(allInteractions)
    }

  override suspend fun resetRecommendationStatusToNew(
    id: SecurityActionRecommendation,
    newTriggeredAt: Instant,
    currentTime: Instant,
  ) = withContext(Dispatchers.Default) {
    databaseProvider.database().securityRecommendationInteractionEntityQueries.insertOrResetToNew(
      recommendationId = id.toDbString(),
      lastRecommendationTriggeredAt = newTriggeredAt,
      recordUpdatedAt = currentTime
    )
  }

  override suspend fun deleteRecommendation(id: SecurityActionRecommendation) =
    withContext(Dispatchers.Default) {
      databaseProvider.database().securityRecommendationInteractionEntityQueries.deleteByIds(
        listOf(id.toDbString())
      )
    }
}
