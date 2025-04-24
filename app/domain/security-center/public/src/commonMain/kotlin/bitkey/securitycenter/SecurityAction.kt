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
enum class SecurityActionRecommendation {
  PAIR_HARDWARE_DEVICE,
  BACKUP_MOBILE_KEY,
  BACKUP_EAK,
  ADD_FINGERPRINTS,
  ADD_TRUSTED_CONTACTS,
  UPDATE_FIRMWARE,
  ENABLE_CRITICAL_ALERTS,
  ENABLE_PUSH_NOTIFICATIONS,
  ENABLE_SMS_NOTIFICATIONS,
  ENABLE_EMAIL_NOTIFICATIONS,
  ADD_BENEFICIARY,
  SETUP_BIOMETRICS,
}

/**
 * Represents the type of security action. Maps 1:1 to classes that implement [SecurityAction].
 */
enum class SecurityActionType {
  HARDWARE_DEVICE,
  BIOMETRIC,
  CRITICAL_ALERTS,
  EAK_BACKUP,
  FINGERPRINTS,
  INHERITANCE,
  MOBILE_KEY_BACKUP,
  SOCIAL_RECOVERY,
}
