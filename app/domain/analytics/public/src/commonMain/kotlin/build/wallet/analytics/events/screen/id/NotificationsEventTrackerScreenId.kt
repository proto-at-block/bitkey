package build.wallet.analytics.events.screen.id

enum class NotificationsEventTrackerScreenId : EventTrackerScreenId {
  /** Screen prompting the customer to set up notification preferences */
  NOTIFICATION_PREFERENCES_SETUP,

  /** Screen prompting the customer to select notification preferences */
  NOTIFICATION_PREFERENCES_SELECTION,

  /** Recovery channels settings screen */
  RECOVERY_CHANNELS_SETTINGS,

  /** Recovery channels settings networking error */
  RECOVERY_CHANNELS_SETTINGS_NETWORKING_ERROR_SHEET,

  /** Recovery channels settings push enable/disable */
  RECOVERY_CHANNELS_SETTINGS_PUSH_TOGGLE_SHEET,

  /** Recovery channels settings edit sms */
  RECOVERY_CHANNELS_SETTINGS_SMS_EDIT_SHEET,

  /** Recovery channels settings us customers cannot use sms */
  RECOVERY_CHANNELS_SETTINGS_SMS_NON_US_SHEET,

  /** Recovery channels settings loading preferences screen */
  RECOVERY_CHANNELS_SETTINGS_LOADING_PREFERENCES,

  /** Recovery channels settings updating server screen */
  RECOVERY_CHANNELS_SETTINGS_UPDATING_PREFERENCES,

  /** Warn user that they should set up all recovery channel options */
  RECOVERY_SKIP_SHEET,

  /** Warn user that email is required as a recovery option */
  RECOVERY_EMAIL_REQUIRED_ERROR_SHEET,

  /** Customer is inputting their phone number */
  SMS_INPUT_ENTERING_SMS,

  /** Customer is inputting the verification code for sms input */
  SMS_INPUT_ENTERING_CODE,

  /** Error shown when adding an sms number to the server fails */
  SMS_INPUT_ERROR_SHEET,

  /** Error shown customer tries to add an sms number that is already active on their account */
  SMS_ALREADY_ACTIVE_ERROR_SHEET,

  /** Error shown customer tries to add an sms number with a country code we don't support */
  SMS_UNSUPPORTED_COUNTRY_ERROR_SHEET,

  /** We are asking for confirmation that the customer wants to skip entering an sms number */
  SMS_SKIP_SHEET,

  /** Error screen shown when the entered verification code is incorrect for sms input */
  SMS_INPUT_INCORRECT_CODE_ERROR,

  /** Error screen shown when the entered verification code is expired for sms input */
  SMS_INPUT_CODE_EXPIRED_ERROR,

  /** Loading screen shown when sending the verification code to the server for sms input */
  SMS_INPUT_SENDING_CODE_TO_SERVER,

  /** Error screen shown when sending the verification code to the server fails for sms input */
  SMS_INPUT_SENDING_CODE_TO_SERVER_ERROR,

  /** Loading screen shown when sending the activation request to the server for sms input */
  SMS_INPUT_SENDING_ACTIVATION_TO_SERVER,

  /** Error screen shown when sending the activation request to the server fails for sms input */
  SMS_INPUT_SENDING_ACTIVATION_TO_SERVER_ERROR,

  /** Customer is inputting their email */
  EMAIL_INPUT_ENTERING_EMAIL,

  /** Customer is inputting the verification code for email input */
  EMAIL_INPUT_ENTERING_CODE,

  /** Error shown when adding an email to the server fails */
  EMAIL_INPUT_ERROR_SHEET,

  /** Error shown customer tries to add an email that is already active on their account */
  EMAIL_ALREADY_ACTIVE_ERROR_SHEET,

  /** Error shown customer tries to add an invalid email */
  EMAIL_INVALID_ERROR_SHEET,

  /** We are asking for confirmation that the customer wants to skip entering an email */
  EMAIL_SKIP_SHEET,

  /** Error screen shown when the entered verification code is expired for email input */
  EMAIL_INPUT_CODE_EXPIRED_ERROR,

  /** Error screen shown when the entered verification code is incorrect for email input */
  EMAIL_INPUT_INCORRECT_CODE_ERROR,

  /** Loading screen shown when sending the verification code to the server for email input */
  EMAIL_INPUT_SENDING_CODE_TO_SERVER,

  /** Error screen shown when sending the verification code to the server fails for email input */
  EMAIL_INPUT_SENDING_CODE_TO_SERVER_ERROR,

  /** Loading screen shown when sending the activation request to the server for email input */
  EMAIL_INPUT_SENDING_ACTIVATION_TO_SERVER,

  /** Error screen shown when sending the activation request to the server fails for email input */
  EMAIL_INPUT_SENDING_ACTIVATION_TO_SERVER_ERROR,

  /** An error shown when the customer attempts to skip both email and sms */
  NO_SKIP_ALLOWED_SHEET,

  /** Error shown when resending a verification code fails */
  RESEND_CODE_ERROR_SHEET,

  /** Prompt for the customer to approve the change to their notifications with their HW */
  NOTIFICATIONS_HW_APPROVAL,

  /** Error sheet shown when validating a change to notification touchpoints fails */
  NOTIFICATIONS_HW_APPROVAL_ERROR_SHEET,

  /** Success screen when notification is approved and activated for sms */
  NOTIFICATIONS_HW_APPROVAL_SUCCESS_SMS,

  /** Success screen when notification is approved and activated for email */
  NOTIFICATIONS_HW_APPROVAL_SUCCESS_EMAIL,

  /** Screen asking the customer to enable push notification permissions */
  ENABLE_PUSH_NOTIFICATIONS,

  /** Loading screen shown when we are completing saving notifications during onboarding */
  SAVE_NOTIFICATIONS_LOADING,

  /** Screen for customer to enter comms verification code when trying to cancel a recovery conflict. */
  CANCELLING_SOMEONE_ELSE_IS_RECOVERING_COMMS_VERIFICATION_ENTRY,
}
