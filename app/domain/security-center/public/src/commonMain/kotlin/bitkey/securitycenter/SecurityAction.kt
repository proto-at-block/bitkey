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
  BACKUP_MOBILE_KEY,
  BACKUP_EAK,
  ADD_FINGERPRINTS,
  ADD_TRUSTED_CONTACTS,
  ENABLE_CRITICAL_ALERTS,
  ADD_BENEFICIARY,
  SETUP_BIOMETRICS,
}
