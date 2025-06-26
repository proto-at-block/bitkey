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
enum class SecurityActionRecommendation(val actionType: SecurityActionType) {
  PAIR_HARDWARE_DEVICE(actionType = SecurityActionType.HARDWARE_DEVICE),
  BACKUP_MOBILE_KEY(actionType = SecurityActionType.APP_KEY_BACKUP),
  BACKUP_EAK(actionType = SecurityActionType.EEK_BACKUP),
  ADD_FINGERPRINTS(actionType = SecurityActionType.FINGERPRINTS),
  ADD_TRUSTED_CONTACTS(actionType = SecurityActionType.SOCIAL_RECOVERY),
  UPDATE_FIRMWARE(actionType = SecurityActionType.HARDWARE_DEVICE),
  ENABLE_CRITICAL_ALERTS(actionType = SecurityActionType.CRITICAL_ALERTS),
  ENABLE_PUSH_NOTIFICATIONS(actionType = SecurityActionType.CRITICAL_ALERTS),
  ENABLE_SMS_NOTIFICATIONS(actionType = SecurityActionType.CRITICAL_ALERTS),
  ENABLE_EMAIL_NOTIFICATIONS(actionType = SecurityActionType.CRITICAL_ALERTS),
  ADD_BENEFICIARY(actionType = SecurityActionType.INHERITANCE),
  SETUP_BIOMETRICS(actionType = SecurityActionType.BIOMETRIC),
  ENABLE_TRANSACTION_VERIFICATION(actionType = SecurityActionType.TRANSACTION_VERIFICATION),
}

/**
 * Represents the type of security action. Maps 1:1 to classes that implement [SecurityAction].
 */
enum class SecurityActionType(val hasEducation: Boolean) {
  HARDWARE_DEVICE(hasEducation = false),
  BIOMETRIC(hasEducation = false),
  CRITICAL_ALERTS(hasEducation = true),
  EEK_BACKUP(hasEducation = true),
  FINGERPRINTS(hasEducation = true),
  INHERITANCE(hasEducation = false),
  APP_KEY_BACKUP(hasEducation = false),
  SOCIAL_RECOVERY(hasEducation = true),
  TRANSACTION_VERIFICATION(hasEducation = false),
}
