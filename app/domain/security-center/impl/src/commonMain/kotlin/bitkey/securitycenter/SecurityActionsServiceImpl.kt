package bitkey.securitycenter

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

/**
 * Implementation of [SecurityActionsService].
 */
@BitkeyInject(AppScope::class)
class SecurityActionsServiceImpl(
  private val mobileKeyBackupHealthActionFactory: MobileKeyBackupHealthActionFactory,
  private val eakBackupHealthActionFactory: EakBackupHealthActionFactory,
  private val socialRecoveryActionFactory: SocialRecoveryActionFactory,
  private val inheritanceActionFactory: InheritanceActionFactory,
  private val biometricActionFactory: BiometricActionFactory,
  private val criticalAlertsActionFactory: CriticalAlertsActionFactory,
  private val fingerprintsActionFactory: FingerprintsActionFactory,
) : SecurityActionsService {
  private var actions: List<SecurityAction> = emptyList()

  override suspend fun getActions(category: SecurityActionCategory): List<SecurityAction> {
    return allActions().filter { it.category() == category }
  }

  override suspend fun getRecommendations(): List<SecurityActionRecommendation> {
    return allActions().map { it.getRecommendations() }.flatten().sortedBy { it.ordinal }
  }

  /**
   * Order of the list is important as it will be used to determine the order of the actions in the UI.
   */
  private suspend fun allActions(): List<SecurityAction> {
    if (actions.isEmpty()) {
      actions = listOfNotNull(
        fingerprintsActionFactory.create(),
        biometricActionFactory.create(),
        criticalAlertsActionFactory.create(),
        socialRecoveryActionFactory.create(),
        mobileKeyBackupHealthActionFactory.create(),
        inheritanceActionFactory.create(),
        eakBackupHealthActionFactory.create()
      )
    }

    return actions
  }
}
