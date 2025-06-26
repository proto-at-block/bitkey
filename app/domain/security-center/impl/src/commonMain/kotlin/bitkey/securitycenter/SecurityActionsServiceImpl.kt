package bitkey.securitycenter

import bitkey.metrics.MetricOutcome
import bitkey.metrics.MetricTrackerService
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_SECURITY_CENTER_CHECK
import build.wallet.database.SecurityInteractionStatus
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Implementation of [SecurityActionsService].
 */
@BitkeyInject(AppScope::class)
class SecurityActionsServiceImpl(
  private val appKeyBackupHealthActionFactory: AppKeyBackupHealthActionFactory,
  private val eekBackupHealthActionFactory: EekBackupHealthActionFactory,
  private val socialRecoveryActionFactory: SocialRecoveryActionFactory,
//  private val inheritanceActionFactory: InheritanceActionFactory,
  private val biometricActionFactory: BiometricActionFactory,
  private val criticalAlertsActionFactory: CriticalAlertsActionFactory,
  private val fingerprintsActionFactory: FingerprintsActionFactory,
  private val hardwareDeviceActionFactory: HardwareDeviceActionFactory,
  private val txVerificationActionFactory: TxVerificationActionFactory,
  private val eventTracker: EventTracker,
  private val metricTrackerService: MetricTrackerService,
  private val securityRecommendationInteractionDao: SecurityRecommendationInteractionDao,
  private val clock: Clock,
) : SecurityActionsService {
  /**
   * Order of the list is important as it will be used to determine the order of the actions in the UI.
   */
  private val factories = listOf(
    SecurityActionType.HARDWARE_DEVICE to hardwareDeviceActionFactory::create,
    SecurityActionType.CRITICAL_ALERTS to criticalAlertsActionFactory::create,
    SecurityActionType.SOCIAL_RECOVERY to socialRecoveryActionFactory::create,
    SecurityActionType.APP_KEY_BACKUP to appKeyBackupHealthActionFactory::create,
    //    SecurityActionType.INHERITANCE to inheritanceActionFactory::create, // TODO: add once we have built dismissible recommendations
    SecurityActionType.EEK_BACKUP to eekBackupHealthActionFactory::create,
    SecurityActionType.FINGERPRINTS to fingerprintsActionFactory::create,
    SecurityActionType.BIOMETRIC to biometricActionFactory::create,
    SecurityActionType.TRANSACTION_VERIFICATION to txVerificationActionFactory::create
  )

  override suspend fun getActions(category: SecurityActionCategory): List<SecurityAction> {
    return allActions().first().filter { it.category() == category }
  }

  override fun getRecommendations(): Flow<List<SecurityActionRecommendation>> {
    return flow {
      allActions().map { actions ->
        actions.flatMap { action ->
          action.getRecommendations().also {
            metricTrackerService.completeMetric(
              metricDefinition = SecurityActionMetricDefinition(action.type()),
              outcome = MetricOutcome.Succeeded
            )
          }
        }
          .sortedBy { it.ordinal }
          .also { recommendations ->
            SecurityActionRecommendation.entries.forEach { entry ->
              if (entry == SecurityActionRecommendation.ADD_BENEFICIARY) {
                return@forEach
              } // TODO: remove once we include inheritance action
              eventTracker.track(
                ACTION_APP_SECURITY_CENTER_CHECK,
                SecurityCenterScreenIdContext(entry, recommendations.contains(entry))
              )
            }
          }
      }.collect(::emit)
    }
  }

  override fun getRecommendationsWithInteractionStatus(): Flow<List<SecurityRecommendationWithStatus>> {
    val activeSystemRecsFlow = getRecommendations()
    return activeSystemRecsFlow.transformLatest { activeSystemRecs ->
      val currentTime = clock.now()
      activeSystemRecs.forEach { recommendation ->
        securityRecommendationInteractionDao.recordRecommendationActive(
          id = recommendation,
          triggeredAt = currentTime,
          currentTime = currentTime
        )
      }
      emitAll(
        securityRecommendationInteractionDao.getAllInteractions().map { interactionsFromDb ->
          val interactionMap = interactionsFromDb.associateBy { it.recommendationId }

          // Remove any persisted recommendations that are no longer active
          interactionMap.keys
            .filter { id -> activeSystemRecs.none { it.name == id } }
            .forEach { idToRemove ->
              securityRecommendationInteractionDao.deleteRecommendation(SecurityActionRecommendation.valueOf(idToRemove))
            }

          SecurityActionRecommendation.entries.mapNotNull { possibleRecommendation ->
            val dbEntry = interactionMap[possibleRecommendation.name]
            val isActiveNow = activeSystemRecs.contains(possibleRecommendation)
            when {
              isActiveNow && dbEntry != null -> SecurityRecommendationWithStatus(
                recommendation = possibleRecommendation,
                interactionStatus = dbEntry.interactionStatus,
                lastRecommendationTriggeredAt = currentTime,
                lastInteractedAt = dbEntry.lastInteractedAt,
                recordUpdatedAt = dbEntry.recordUpdatedAt
              )
              isActiveNow -> SecurityRecommendationWithStatus(
                recommendation = possibleRecommendation,
                interactionStatus = SecurityInteractionStatus.NEW,
                lastRecommendationTriggeredAt = currentTime,
                lastInteractedAt = null,
                recordUpdatedAt = currentTime
              )
              else -> null
            }
          }
        }
      )
    }
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
    securityRecommendationInteractionDao.recordUserInteraction(
      id = id,
      status = status,
      interactedAt = interactedAt,
      currentTime = clock.now()
    )
    return Result.success(Unit)
  }

  private suspend fun allActions(): Flow<List<SecurityAction>> {
    return combine(
      factories.map { (actionType, factoryMethod) ->
        factoryMethod().map { action ->
          metricTrackerService.startMetric(
            metricDefinition = SecurityActionMetricDefinition(actionType)
          )
          if (action == null) {
            metricTrackerService.completeMetric(
              metricDefinition = SecurityActionMetricDefinition(actionType),
              outcome = MetricOutcome.Failed
            )
          }
          action
        }
      }
    ) {
      it.filterNotNull().toList()
    }
  }

  override suspend fun markAllRecommendationsViewed() {
    getRecommendationsWithInteractionStatus()
      .first()
      .filter { it.interactionStatus != SecurityInteractionStatus.VIEWED }
      .forEach { rec ->
        recordUserInteractionWithRecommendation(
          id = rec.recommendation,
          status = SecurityInteractionStatus.VIEWED,
          interactedAt = clock.now()
        )
      }
  }
}
