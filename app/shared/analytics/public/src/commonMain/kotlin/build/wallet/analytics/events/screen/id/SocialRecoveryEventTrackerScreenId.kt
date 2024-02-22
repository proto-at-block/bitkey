package build.wallet.analytics.events.screen.id

enum class SocialRecoveryEventTrackerScreenId : EventTrackerScreenId {
  /** Customer is adding a new Trusted Contact and entering the name of the contact. */
  TC_ADD_TC_NAME,

  /** Customer is viewing the 'Trusted Contact' list screen from the settings page */
  TC_MANAGEMENT_SETTINGS_LIST,

  /** Lite Customer is viewing the 'Trusted Contact' list screen from the settings page */
  TC_MANAGEMENT_SETTINGS_LIST_LITE,

  /** Trusted contact is entering an invite code */
  TC_ENROLLMENT_ENTER_INVITE_CODE,

  /** We are retrieving the invite data from f8e for a given code */
  TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E,

  /** Failure screen shown when there was an error retrieving the invite data from f8e for a given code */
  TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE,

  /** Trusted contact is adding the name of the customer they are trying to protect */
  TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME,

  /** Trusted contact is getting or creating trusted contact identity key. */
  TC_TRUSTED_CONTACT_IDENTITY_KEY,

  /** Failure while trusted contact is getting or creating trusted contact identity key. */
  TC_TRUSTED_CONTACT_IDENTITY_KEY_FAILURE,

  /** We are accepting the invite data from f8e for a given code */
  TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E,

  /** Failure screen shown when there was an error accepting the invite data from f8e for a given code */
  TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE,

  /** Trusted contact is loading the identity key */
  TC_ENROLLMENT_LOAD_KEY,

  /** Identity Key Loading failure */
  TC_ENROLLMENT_LOAD_KEY_FAILURE,

  /** Trusted contact is authing with hardware to confirm adding a TC */
  TC_ENROLLMENT_ADD_TC_HARDWARE_CHECK,

  /** Trusted contact has been added on the server, share invite screen */
  TC_ENROLLMENT_SHARE_SCREEN,

  /** Success screen shown when the TC enrollment is complete */
  TC_ENROLLMENT_SUCCESS,

  /** Customer is viewing the details of an invited or confirmed TC  */
  TC_MANAGEMENT_DETAIL_SHEET,

  /** Customer is viewing the removal screen for an invited or  confirmed TC  */
  TC_MANAGEMENT_REMOVAL_CONFIRMATION,

  /** The bottom sheet showing the details of a protected customer that a TC is protected is showing */
  TC_PROTECTED_CUSTOMER_SHEET,

  /** The bottom sheet showing an error message after trying to remove the PC */
  TC_PROTECTED_CUSTOMER_SHEET_REMOVAL_FAILURE,

  /** Trusted contact is helping protect customer recover and selects how they got in touch  */
  TC_RECOVERY_GET_IN_TOUCH,

  /** Trusted contact is helping protect customer recover and receives warning about communication method */
  TC_RECOVERY_SECURITY_NOTICE,

  /** Trusted contact is helping protect customer recover and is confirming identity of protect customer */
  TC_RECOVERY_CONTACT_CONFIRMATION,

  /** Trusted contact is helping protect customer recover and entering code from protected customer */
  TC_RECOVERY_CODE_VERIFICATION,

  /** Trusted contact's entered code code passed verification. */
  TC_RECOVERY_CODE_VERIFICATION_SUCCESS,

  /** Trusted contact's entered code code failed verification. */
  TC_RECOVERY_CODE_VERIFICATION_FAILURE,

  /** Loading screen when starting a new Social Recovery Challenge */
  RECOVERY_CHALLENGE_STARTING,

  /** Failed to start a recovery challenge */
  RECOVERY_CHALLENGE_FAILED,

  /** List of Trusted contacts used for completing a Social Recovery Challenge */
  RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST,

  /** Trusted Contact Verification Screen */
  RECOVERY_CHALLENGE_TC_VERIFICATION_CODE,

  /** Restoring App Key after a Social Recovery Challenge */
  RECOVERY_CHALLENGE_RESTORE_APP_KEY,

  /** Failed to complete a Social Recovery Challenge */
  RECOVERY_CHALLENGE_RECOVERY_FAILED,
}
