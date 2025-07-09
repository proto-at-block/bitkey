package bitkey.securitycenter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class FakeAction(
  private val recommendations: List<SecurityActionRecommendation>,
  private val category: SecurityActionCategory,
  private val type: SecurityActionType,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    return recommendations
  }

  override fun category(): SecurityActionCategory {
    return category
  }

  override fun type(): SecurityActionType = type

  override fun state(): SecurityActionState {
    return if (recommendations.isEmpty()) {
      SecurityActionState.Secure
    } else {
      SecurityActionState.HasRecommendationActions
    }
  }
}

abstract class FakeActionFactory(
  private val recommendations: List<SecurityActionRecommendation>,
  private val category: SecurityActionCategory,
  private val type: SecurityActionType,
) {
  var includeRecommendations: Boolean = true

  private fun getRecommendations(): List<SecurityActionRecommendation> {
    return if (includeRecommendations) recommendations else emptyList()
  }

  fun createAction(): SecurityAction {
    return FakeAction(
      recommendations = getRecommendations(),
      category = category,
      type = type
    )
  }
}

class AppKeyCloudBackupHealthActionFactoryFake : AppKeyBackupHealthActionFactory,
  FakeActionFactory(
    recommendations = listOf(SecurityActionRecommendation.BACKUP_MOBILE_KEY),
    category = SecurityActionCategory.RECOVERY,
    type = SecurityActionType.APP_KEY_BACKUP
  ) {
  override suspend fun create(): Flow<SecurityAction> = flowOf(createAction())
}

class EekCloudBackupHealthActionFactoryFake : EekBackupHealthActionFactory,
  FakeActionFactory(
    recommendations = listOf(SecurityActionRecommendation.BACKUP_EAK),
    category = SecurityActionCategory.RECOVERY,
    type = SecurityActionType.EEK_BACKUP
  ) {
  override suspend fun create(): Flow<SecurityAction> = flowOf(createAction())
}

class SocialRecoveryActionFactoryFake : SocialRecoveryActionFactory,
  FakeActionFactory(
    recommendations = listOf(SecurityActionRecommendation.ADD_TRUSTED_CONTACTS),
    category = SecurityActionCategory.RECOVERY,
    type = SecurityActionType.SOCIAL_RECOVERY
  ) {
  override suspend fun create(): Flow<SecurityAction> = flowOf(createAction())
}

class InheritanceActionFactoryFake : InheritanceActionFactory,
  FakeActionFactory(
    recommendations = listOf(SecurityActionRecommendation.ADD_BENEFICIARY),
    category = SecurityActionCategory.RECOVERY,
    type = SecurityActionType.INHERITANCE
  ) {
  override suspend fun create(): Flow<SecurityAction> = flowOf(createAction())
}

class BiometricActionFactoryFake : BiometricActionFactory,
  FakeActionFactory(
    recommendations = listOf(SecurityActionRecommendation.SETUP_BIOMETRICS),
    category = SecurityActionCategory.SECURITY,
    type = SecurityActionType.BIOMETRIC
  ) {
  override suspend fun create(): Flow<SecurityAction> = flowOf(createAction())
}

class CriticalAlertsActionFactoryFake : CriticalAlertsActionFactory,
  FakeActionFactory(
    recommendations = listOf(SecurityActionRecommendation.ENABLE_CRITICAL_ALERTS),
    category = SecurityActionCategory.RECOVERY,
    type = SecurityActionType.CRITICAL_ALERTS
  ) {
  override suspend fun create(): Flow<SecurityAction> = flowOf(createAction())
}

class FingerprintsActionFactoryFake : FingerprintsActionFactory,
  FakeActionFactory(
    recommendations = listOf(
      SecurityActionRecommendation.ADD_FINGERPRINTS,
      SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET
    ),
    category = SecurityActionCategory.SECURITY,
    type = SecurityActionType.FINGERPRINTS
  ) {
  override suspend fun create(): Flow<SecurityAction> = flowOf(createAction())
}

class HardwareDeviceActionFactoryFake : HardwareDeviceActionFactory,
  FakeActionFactory(
    recommendations = listOf(
      SecurityActionRecommendation.UPDATE_FIRMWARE,
      SecurityActionRecommendation.PAIR_HARDWARE_DEVICE
    ),
    category = SecurityActionCategory.SECURITY,
    type = SecurityActionType.HARDWARE_DEVICE
  ) {
  override suspend fun create(): Flow<SecurityAction> = flowOf(createAction())
}

class TxVerificationActionFactoryFake : TxVerificationActionFactory, FakeActionFactory(
  recommendations = listOf(
    SecurityActionRecommendation.ENABLE_TRANSACTION_VERIFICATION
  ),
  category = SecurityActionCategory.SECURITY,
  type = SecurityActionType.TRANSACTION_VERIFICATION
) {
  override suspend fun create(): Flow<SecurityAction> = flowOf(createAction())
}
