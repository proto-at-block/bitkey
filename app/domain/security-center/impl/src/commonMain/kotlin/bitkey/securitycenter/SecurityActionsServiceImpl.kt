package bitkey.securitycenter

import bitkey.metrics.MetricOutcome
import bitkey.metrics.MetricTrackerService
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.flow.*

/**
 * Implementation of [SecurityActionsService].
 */
@BitkeyInject(AppScope::class)
class SecurityActionsServiceImpl(
  private val mobileKeyBackupHealthActionFactory: MobileKeyBackupHealthActionFactory,
  private val eakBackupHealthActionFactory: EakBackupHealthActionFactory,
  private val socialRecoveryActionFactory: SocialRecoveryActionFactory,
//  private val inheritanceActionFactory: InheritanceActionFactory,
  private val biometricActionFactory: BiometricActionFactory,
  private val criticalAlertsActionFactory: CriticalAlertsActionFactory,
  private val fingerprintsActionFactory: FingerprintsActionFactory,
  private val hardwareDeviceActionFactory: HardwareDeviceActionFactory,
  private val eventTracker: EventTracker,
  private val metricTrackerService: MetricTrackerService,
) : SecurityActionsService {
  /**
   * Order of the list is important as it will be used to determine the order of the actions in the UI.
   */
  private val factories = listOf(
    SecurityActionType.HARDWARE_DEVICE to hardwareDeviceActionFactory::create,
    SecurityActionType.CRITICAL_ALERTS to criticalAlertsActionFactory::create,
    SecurityActionType.SOCIAL_RECOVERY to socialRecoveryActionFactory::create,
    SecurityActionType.MOBILE_KEY_BACKUP to mobileKeyBackupHealthActionFactory::create,
    //    SecurityActionType.INHERITANCE to inheritanceActionFactory::create, // TODO: add once we have built dismissible recommendations
    SecurityActionType.EAK_BACKUP to eakBackupHealthActionFactory::create,
    SecurityActionType.FINGERPRINTS to fingerprintsActionFactory::create,
    SecurityActionType.BIOMETRIC to biometricActionFactory::create
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
          .sortedBy { recommendation -> recommendation.ordinal }
          .also {
            SecurityActionRecommendation.entries.forEach { recommendation ->
              if (recommendation == SecurityActionRecommendation.ADD_BENEFICIARY) { // TODO: remove once we include inheritance action
                return@forEach
              }
              val isPending = it.contains(recommendation)
              eventTracker.track(
                Action.ACTION_APP_SECURITY_CENTER_CHECK,
                SecurityCenterScreenIdContext(recommendation, isPending)
              )
            }
          }
      }.collect(::emit)
    }
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
}
