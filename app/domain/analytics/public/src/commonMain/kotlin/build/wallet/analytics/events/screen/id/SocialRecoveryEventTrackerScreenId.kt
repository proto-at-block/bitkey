package build.wallet.analytics.events.screen.id

enum class SocialRecoveryEventTrackerScreenId : EventTrackerScreenId {
  /** Education screens shown before adding a RC for inheritance. */
  TC_ADD_INHERITANCE_EXPLAINER,

  /** Setup text shown before adding a RC for inheritance. */
  TC_ADD_INHERITANCE_SETUP,

  /** Customer is adding a new Beneficiary and entering the name of the contact. */
  TC_BENEFICIARY_ADD_TC_NAME,

  /** Customer is adding a new Recovery Contact and entering the name of the contact. */
  TC_ADD_TC_NAME,

  /** Customer is viewing the Recovery Contact list screen from the settings page */
  TC_MANAGEMENT_SETTINGS_LIST,

  /** Lite Customer is viewing the Recovery Contact list screen from the settings page */
  TC_MANAGEMENT_SETTINGS_LIST_LITE,

  /** Showing the beneficiary onboarding page **/
  TC_BENEFICIARY_ONBOARDING,

  /** Contact is entering an invite code from the inheritance management UI */
  TC_BENEFICIARY_ENROLLMENT_ENTER_INVITE_CODE,

  /** Recovery Contact is entering an invite code */
  TC_ENROLLMENT_ENTER_INVITE_CODE,

  /** We are retrieving the inheritance invite data from f8e for a given code */
  TC_BENEFICIARY_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E,

  /** We are retrieving the invite data from f8e for a given code */
  TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E,

  /** Failure screen shown when there was an error retrieving the invite data from f8e for a given code */
  TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE,

  /** Recovery Contact is adding the name of the customer they are trying to protect. Also signals a successful retrieval of the invite from F8E. */
  TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME,

  /** Beneficiary is entering a name for the benefactor */
  TC_ENROLLMENT_TC_ADD_BENEFACTOR_NAME,

  /** Recovery Contact is getting or creating Recovery Contact identity key. */
  TC_DELEGATED_DECRYPTION_KEY_KEY,

  /** Failure while Recovery Contact is getting or creating Recovery Contact identity key. */
  TC_DELEGATED_DECRYPTION_KEY_KEY_FAILURE,

  /** We are accepting the inheritance invite data from f8e for a given code */
  TC_BENEFICIARY_ENROLLMENT_ACCEPT_INVITE_WITH_F8E,

  /** We are accepting the invite data from f8e for a given code */
  TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E,

  /** Failure screen shown when there was an error accepting the invite data from f8e for a given code */
  TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE,

  /** Recovery Contact is loading the inheritance identity key */
  TC_BENEFICIARY_ENROLLMENT_LOAD_KEY,

  /** Recovery Contact is loading the identity key */
  TC_ENROLLMENT_LOAD_KEY,

  /** Identity Key Loading failure */
  TC_ENROLLMENT_LOAD_KEY_FAILURE,

  /** Upload the sealed delegated decryption key to the server for inheritance */
  TC_BENEFICIARY_ENROLLMENT_UPLOAD_DELEGATED_DECRYPTION_KEY,

  /** Upload the sealed delegated decryption key to the server */
  TC_ENROLLMENT_UPLOAD_DELEGATED_DECRYPTION_KEY,

  /** Failed to upload sealed delegated decryption key */
  TC_ENROLLMENT_UPLOAD_DELEGATED_DECRYPTION_KEY_FAILURE,

  /** Recovery Contact is authing with hardware to confirm adding a Beneficiary */
  TC_BENEFICIARY_ENROLLMENT_ADD_TC_HARDWARE_CHECK,

  /** Recovery Contact is authing with hardware to confirm adding a RC */
  TC_ENROLLMENT_ADD_TC_HARDWARE_CHECK,

  /** Beneficiary has been added on the server, share invite screen */
  TC_BENEFICIARY_ENROLLMENT_SHARE_SCREEN,

  /** Recovery Contact has been added on the server, share invite screen */
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

  /** Success screen shown when the RC has accepted an invite */
  TC_ENROLLMENT_INVITE_ACCEPTED,

  /** Screen shown when enrolling as beneficiary and asking for hw device  */
  TC_ENROLLMENT_ASKING_IF_HAS_HARDWARE,

  /** Customer is viewing the explanation of the RC experience */
  TC_MANAGEMENT_EXPLAINER,

  /** Customer is viewing the details of an invited or confirmed RC, within the money home context */
  TC_MANAGEMENT_DETAIL_SHEET,

  /** Customer is viewing the removal screen for an invited or  confirmed RC  */
  TC_MANAGEMENT_REMOVAL_CONFIRMATION,

  /** Customer is attempting to remove an invited or confirmed RC  */
  TC_MANAGEMENT_REMOVAL_LOADING,

  /** Customer failed to remove an invited or confirmed RC  */
  TC_MANAGEMENT_REMOVAL_FAILED,

  /** The bottom sheet showing the details of a protected customer that a RC is protected is showing */
  TC_PROTECTED_CUSTOMER_SHEET,

  /** The bottom sheet showing an error message after trying to remove the PC */
  TC_PROTECTED_CUSTOMER_SHEET_REMOVAL_FAILURE,

  /** Recovery Contact is helping protect customer recover and selects how they got in touch  */
  TC_RECOVERY_GET_IN_TOUCH,

  /** Recovery Contact is helping protect customer recover and receives warning about communication method */
  TC_RECOVERY_SECURITY_NOTICE,

  /** Recovery Contact is helping protect customer recover and is confirming identity of protect customer */
  TC_RECOVERY_CONTACT_CONFIRMATION,

  /** Recovery Contact is helping protect customer recover and entering code from protected customer */
  TC_RECOVERY_CODE_VERIFICATION,

  /** Recovery Contact's entered code passed verification. */
  TC_RECOVERY_CODE_VERIFICATION_SUCCESS,

  /** Recovery Contact's entered code failed verification. */
  TC_RECOVERY_CODE_VERIFICATION_FAILURE,

  /** Loading screen when starting a new Social Recovery Challenge */
  RECOVERY_CHALLENGE_STARTING,

  /** Failed to start a recovery challenge */
  RECOVERY_CHALLENGE_FAILED,

  /** List of Recovery Contacts used for completing a Social Recovery Challenge */
  RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST,

  /** Recovery Contact Verification Screen */
  RECOVERY_CHALLENGE_TC_VERIFICATION_CODE,

  /** Restoring App Key after a Social Recovery Challenge */
  RECOVERY_CHALLENGE_RESTORE_APP_KEY,

  /** Failed to complete a Social Recovery Challenge */
  RECOVERY_CHALLENGE_RECOVERY_FAILED,

  /** Benefactor invite accepted, tapped notification and showing success screen */
  BENEFACTOR_INVITE_ACCEPTED,

  /** Loading information about benefactor invitation */
  BENEFACTOR_RECOVERY_RELATIONSHIP_LOADING,

  /** Awaiting Benefactor endorsement */
  BENEFACTOR_RECOVERY_RELATIONSHIP_AWAITING_ENDORSEMENT,

  /** Benefactor invite accepted, tapped notification but the relationship is no longer active leading to an error screen */
  BENEFACTOR_RECOVERY_RELATIONSHIP_NOT_ACTIVE,

  /** Protected customer invite accepted, tapped notification and showing success screen */
  PROTECTED_CUSTOMER_INVITE_ACCEPTED,

  /** Protected customer invite accepted, tapped notification but the relationship is no longer active leading to an error screen */
  PROTECTED_CUSTOMER_RECOVERY_RELATIONSHIP_NOT_ACTIVE,

  /** Loading information about protected customer invitation */
  PROTECTED_CUSTOMER_RECOVERY_RELATIONSHIP_LOADING,

  /** Awaiting Protected customer endorsement */
  PROTECTED_CUSTOMER_RECOVERY_RELATIONSHIP_AWAITING_ENDORSEMENT,
}
