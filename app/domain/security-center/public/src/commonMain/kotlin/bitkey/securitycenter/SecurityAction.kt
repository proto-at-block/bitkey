package bitkey.securitycenter

/**
 * Represents a security action that the customer can take to improve their account's security.
 */
interface SecurityAction {
  /**
   * Returns the security recommendations. Dynamic based on the current status of the action.
   */
  fun getRecommendations(): List<SecurityActionRecommendation>

  /**
   * Returns the category of the action.
   */
  fun category(): SecurityActionCategory

  /**
   * Returns the type of the action.
   */
  fun type(): SecurityActionType

  fun state(): SecurityActionState

  fun requiresAction(): Boolean {
    return getRecommendations().isNotEmpty()
  }
}

enum class SecurityActionCategory {
  SECURITY,
  RECOVERY,
}

/**
 * Represents a recommendation that the customer can take to improve their account's security.
 * In priority order.
 */
enum class SecurityActionRecommendation(
  val actionType: SecurityActionType,
  val hasEducation: Boolean,
) {
  COMPLETE_FINGERPRINT_RESET(
    actionType = SecurityActionType.FINGERPRINTS,
    hasEducation = false
  ),
  PAIR_HARDWARE_DEVICE(
    actionType = SecurityActionType.HARDWARE_DEVICE,
    hasEducation = false
  ),
  BACKUP_MOBILE_KEY(
    actionType = SecurityActionType.APP_KEY_BACKUP,
    hasEducation = false
  ),
  BACKUP_EAK(
    actionType = SecurityActionType.EEK_BACKUP,
    hasEducation = true
  ),
  ADD_FINGERPRINTS(
    actionType = SecurityActionType.FINGERPRINTS,
    hasEducation = true
  ),
  ADD_TRUSTED_CONTACTS(
    actionType = SecurityActionType.SOCIAL_RECOVERY,
    hasEducation = true
  ),
  UPDATE_FIRMWARE(
    actionType = SecurityActionType.HARDWARE_DEVICE,
    hasEducation = false
  ),
  ENABLE_CRITICAL_ALERTS(
    actionType = SecurityActionType.CRITICAL_ALERTS,
    hasEducation = true
  ),
  ENABLE_PUSH_NOTIFICATIONS(
    actionType = SecurityActionType.CRITICAL_ALERTS,
    hasEducation = true
  ),
  ENABLE_SMS_NOTIFICATIONS(
    actionType = SecurityActionType.CRITICAL_ALERTS,
    hasEducation = true
  ),
  ENABLE_EMAIL_NOTIFICATIONS(
    actionType = SecurityActionType.CRITICAL_ALERTS,
    hasEducation = true
  ),
  PROVISION_APP_KEY_TO_HARDWARE(
    actionType = SecurityActionType.FINGERPRINTS,
    hasEducation = false
  ),
  ADD_BENEFICIARY(
    actionType = SecurityActionType.INHERITANCE,
    hasEducation = false
  ),
  SETUP_BIOMETRICS(
    actionType = SecurityActionType.BIOMETRIC,
    hasEducation = false
  ),
  ENABLE_TRANSACTION_VERIFICATION(
    actionType = SecurityActionType.TRANSACTION_VERIFICATION,
    hasEducation = true
  ),
}

/**
 * Represents the type of security action. Maps 1:1 to classes that implement [SecurityAction].
 */
enum class SecurityActionType {
  HARDWARE_DEVICE,
  BIOMETRIC,
  CRITICAL_ALERTS,
  EEK_BACKUP,
  FINGERPRINTS,
  INHERITANCE,
  APP_KEY_BACKUP,
  SOCIAL_RECOVERY,
  TRANSACTION_VERIFICATION,
}

enum class SecurityActionState {
  Secure,
  HasRecommendationActions,
  HasCriticalActions,
  Disabled,
}
