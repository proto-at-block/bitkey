package bitkey.securitycenter

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.SecurityInteractionStatus
import build.wallet.database.sqldelight.SecurityRecommendationInteractionEntity
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.getOrThrow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
  ) {
    databaseProvider.database().awaitTransaction {
      securityRecommendationInteractionEntityQueries.insertOrUpdateActiveRecommendation(
        recommendationId = id.toDbString(),
        interactionStatus = SecurityInteractionStatus.NEW,
        lastInteractedAt = null,
        lastRecommendationTriggeredAt = triggeredAt,
        recordUpdatedAt = currentTime
      )
    }.getOrThrow()
  }

  override suspend fun recordUserInteraction(
    id: SecurityActionRecommendation,
    status: SecurityInteractionStatus,
    interactedAt: Instant,
    currentTime: Instant,
  ) {
    databaseProvider.database().awaitTransaction {
      securityRecommendationInteractionEntityQueries.updateUserInteraction(
        recommendationId = id.toDbString(),
        interactionStatus = status,
        lastInteractedAt = interactedAt,
        recordUpdatedAt = currentTime
      )
    }.getOrThrow()
  }

  override fun getInteraction(
    id: SecurityActionRecommendation,
  ): Flow<SecurityRecommendationInteractionEntity> =
    flow {
      val interaction = databaseProvider.database().securityRecommendationInteractionEntityQueries
        .getById(id.toDbString())
        .awaitAsOneOrNullResult()
        .getOrThrow()
      requireNotNull(interaction) { "No record found for id: $id" }
      emit(interaction)
    }

  override fun getInteractions(
    ids: Set<SecurityActionRecommendation>,
  ): Flow<Map<SecurityActionRecommendation, SecurityRecommendationInteractionEntity>> =
    flow {
      val interactions: List<SecurityRecommendationInteractionEntity> =
        databaseProvider.database().securityRecommendationInteractionEntityQueries
          .getByIds(ids.toDbStrings())
          .awaitAsListResult()
          .getOrThrow()

      val interactionMap = interactions.associateBy(
        keySelector = { entity -> entity.recommendationId.toSecurityActionRecommendation() },
        valueTransform = { entity -> entity }
      )

      emit(interactionMap)
    }

  override fun getAllInteractions(): Flow<List<SecurityRecommendationInteractionEntity>> =
    flow {
      val allInteractions = databaseProvider.database().securityRecommendationInteractionEntityQueries
        .getAll()
        .awaitAsListResult()
        .getOrThrow()
      emit(allInteractions)
    }

  override suspend fun deleteRecommendation(id: String) {
    databaseProvider.database().awaitTransaction {
      securityRecommendationInteractionEntityQueries.deleteByIds(
        listOf(id)
      )
    }.getOrThrow()
  }

  override suspend fun clear() {
    databaseProvider.database().awaitTransaction {
      securityRecommendationInteractionEntityQueries.deleteAll()
    }.getOrThrow()
  }
}
