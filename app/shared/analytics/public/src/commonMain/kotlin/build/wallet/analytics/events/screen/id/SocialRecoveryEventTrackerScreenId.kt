package build.wallet.analytics.events.screen.id

enum class SocialRecoveryEventTrackerScreenId : EventTrackerScreenId {
  /** Eduction screens shown before adding a TC for inheritance. */
  TC_ADD_INHERITANCE_EXPLAINER,

  /** Setup text shown before adding a TC for inheritance. */
  TC_ADD_INHERITANCE_SETUP,

  /** Customer is adding a new Beneficiary and entering the name of the contact. */
  TC_BENEFICIARY_ADD_TC_NAME,

  /** Customer is adding a new Trusted Contact and entering the name of the contact. */
  TC_ADD_TC_NAME,

  /** Customer is viewing the 'Trusted Contact' list screen from the settings page */
  TC_MANAGEMENT_SETTINGS_LIST,

  /** Lite Customer is viewing the 'Trusted Contact' list screen from the settings page */
  TC_MANAGEMENT_SETTINGS_LIST_LITE,

  /** Showing the beneficiary onboarding page **/
  TC_BENEFICIARY_ONBOARDING,

  /** Contact is entering an invite code from the inheritance management UI */
  TC_BENEFICIARY_ENROLLMENT_ENTER_INVITE_CODE,

  /** Trusted contact is entering an invite code */
  TC_ENROLLMENT_ENTER_INVITE_CODE,

  /** We are retrieving the inheritance invite data from f8e for a given code */
  TC_BENEFICIARY_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E,

  /** We are retrieving the invite data from f8e for a given code */
  TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E,

  /** Failure screen shown when there was an error retrieving the invite data from f8e for a given code */
  TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE,

  /** Trusted contact is adding the name of the customer they are trying to protect. Also signals a successful retrieval of the invite from F8E. */
  TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME,

  /** Beneficiary is entering a name for the benefactor */
  TC_ENROLLMENT_TC_ADD_BENEFACTOR_NAME,

  /** Trusted contact is getting or creating trusted contact identity key. */
  TC_DELEGATED_DECRYPTION_KEY_KEY,

  /** Failure while trusted contact is getting or creating trusted contact identity key. */
  TC_DELEGATED_DECRYPTION_KEY_KEY_FAILURE,

  /** We are accepting the inheritance invite data from f8e for a given code */
  TC_BENEFICIARY_ENROLLMENT_ACCEPT_INVITE_WITH_F8E,

  /** We are accepting the invite data from f8e for a given code */
  TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E,

  /** Failure screen shown when there was an error accepting the invite data from f8e for a given code */
  TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE,

  /** Trusted contact is loading the inheritance identity key */
  TC_BENEFICIARY_ENROLLMENT_LOAD_KEY,

  /** Trusted contact is loading the identity key */
  TC_ENROLLMENT_LOAD_KEY,

  /** Identity Key Loading failure */
  TC_ENROLLMENT_LOAD_KEY_FAILURE,

  /** Upload the sealed delegated decryption key to the server for inheritance */
  TC_BENEFICIARY_ENROLLMENT_UPLOAD_DELEGATED_DECRYPTION_KEY,

  /** Upload the sealed delegated decryption key to the server */
  TC_ENROLLMENT_UPLOAD_DELEGATED_DECRYPTION_KEY,

  /** Failed to upload sealed delegated decryption key */
  TC_ENROLLMENT_UPLOAD_DELEGATED_DECRYPTION_KEY_FAILURE,

  /** Trusted contact is authing with hardware to confirm adding a Beneficiary */
  TC_BENEFICIARY_ENROLLMENT_ADD_TC_HARDWARE_CHECK,

  /** Trusted contact is authing with hardware to confirm adding a TC */
  TC_ENROLLMENT_ADD_TC_HARDWARE_CHECK,

  /** Beneficiary has been added on the server, share invite screen */
  TC_BENEFICIARY_ENROLLMENT_SHARE_SCREEN,

  /** Trusted contact has been added on the server, share invite screen */
  TC_ENROLLMENT_SHARE_SCREEN,

  /** The protected customer successfully sent an inheritance invite. */
  TC_BENEFICIARY_ENROLLMENT_INVITE_SENT,

  /** The protected customer successfully sent an invite. */
  TC_ENROLLMENT_INVITE_SENT,

  /** The protected customer successful re-sent an inheritance invite. */
  TC_BENEFICIARY_ENROLLMENT_REINVITE_SENT,

  /** The protected customer successful re-sent an invite. */
  TC_ENROLLMENT_REINVITE_SENT,

  /** Error screen shown when an inheritance reinvitation fails. */
  TC_BENEFICIARY_ENROLLMENT_REINVITE_FAILED,

  /** Error screen shown when a reinvitation fails. */
  TC_ENROLLMENT_REINVITE_FAILED,

  /** Success screen shown when the beneficiary has accepted an invite */
  TC_BENEFICIARY_ENROLLMENT_INVITE_ACCEPTED,

  /** Success screen shown when the TC has accepted an invite */
  TC_ENROLLMENT_INVITE_ACCEPTED,

  /** Screen shown when enrolling as beneficiary and asking for hw device  */
  TC_ENROLLMENT_ASKING_IF_HAS_HARDWARE,

  /** Customer is viewing the explanation of the TC experience */
  TC_MANAGEMENT_EXPLAINER,

  /** Customer is viewing the details of an invited or confirmed TC, within the money home context */
  TC_MANAGEMENT_DETAIL_SHEET,

  /** Customer is viewing the removal screen for an invited or  confirmed TC  */
  TC_MANAGEMENT_REMOVAL_CONFIRMATION,

  /** Customer is attempting to remove an invited or confirmed TC  */
  TC_MANAGEMENT_REMOVAL_LOADING,

  /** Customer failed to remove an invited or confirmed TC  */
  TC_MANAGEMENT_REMOVAL_FAILED,

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

  /** Benefactor invite accepted, tapped notification and showing success screen */
  BENEFACTOR_INVITE_ACCEPTED,
}
