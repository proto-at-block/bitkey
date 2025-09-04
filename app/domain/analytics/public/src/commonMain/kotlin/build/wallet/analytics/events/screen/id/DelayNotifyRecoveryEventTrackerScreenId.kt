package build.wallet.analytics.events.screen.id

enum class DelayNotifyRecoveryEventTrackerScreenId : EventTrackerScreenId {
  /** Error screen shown when initiating lost app D+N fails */
  LOST_APP_DELAY_NOTIFY_INITIATION_ERROR,

  /** Instructions to the customer to start app recovery via D+N */
  LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS,

  /** Loading screen shown when awaiting auth challenge for app D+N */
  LOST_APP_DELAY_NOTIFY_INITIATION_AWAITING_AUTH_CHALLENGE,

  /** Loading screen shown when authenticating with the server for app D+N */
  LOST_APP_DELAY_NOTIFY_INITIATION_AUTHENTICATING_WITH_F8E,

  /** Loading screen shown when authenticating with the server for app D+N */
  LOST_APP_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY,

  /** App recovery via D+N is in progress */
  LOST_APP_DELAY_NOTIFY_PENDING,

  /** App recovery via D+N is ready to complete */
  LOST_APP_DELAY_NOTIFY_READY,

  /** Loading screen shown when rotating auth keys for app D+N */
  LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS,

  /** Loading screen shown when listing keysets for app D+N */
  LOST_APP_DELAY_NOTIFY_LISTING_KEYSETS,

  /** Loading screen shown when creating spending keys for app D+N */
  LOST_APP_DELAY_NOTIFY_CREATING_SPENDING_KEYS,

  /** Loading screen shown when activating spending keys for app D+N */
  LOST_APP_DELAY_NOTIFY_ACTIVATING_SPENDING_KEYS,

  /** Error screen shown when creating spending keys fails for lost app D+N */
  LOST_APP_DELAY_NOTIFY_CREATING_SPENDING_KEYS_ERROR,

  /** Error screen shown when activating spending keys fails for lost app D+N */
  LOST_APP_DELAY_NOTIFY_ACTIVATING_SPENDING_KEYS_ERROR,

  /** Failure screen shown when loading sealed DDK fails */
  LOST_APP_DELAY_NOTIFY_DDK_LOADING_ERROR,

  /** Failure screen shown when uploading encrypted descriptors fails */
  LOST_APP_DELAY_NOTIFY_ENCRYPTED_DESCRIPTORS_UPLOAD_ERROR,

  /** Failure screen shown when loading ddk fails */
  LOST_APP_DELAY_NOTIFY_DDK_RECOVERY_DATA_LOADING_ERROR,

  /** Failed to sync socrec relationships error */
  LOST_HW_DELAY_NOTIFY_TRUSTED_CONTACT_SYNC_ERROR,

  /** Loading screen shown when generating PSBTs for app D+N */
  LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS,

  /** Loading screen shown when broadcating sweep for app D+N */
  LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING,

  /** Success screen shown when sweep for app D+N succeeds */
  LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS,

  /** Failure screen shown when sweep for app D+N fails */
  LOST_APP_DELAY_NOTIFY_SWEEP_FAILED,

  /** Prompt to sign PSBTs for app D+N */
  LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT,

  /** Screen shown when the recovered account has a zero balance */
  LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE,

  /** Error screen shown when generating the sweep PSBTs fails for lost app D+N */
  LOST_APP_DELAY_NOTIFY_SWEEP_GENERATE_PSBTS_ERROR,

  /** Error screen shown when user navigates away from sweep */
  LOST_APP_DELAY_NOTIFY_SWEEP_EXITED,

  /** Error screen shown when an error was encountered uploading cloud backup */
  LOST_APP_DELAY_NOTIFY_BACKUP_UPLOAD_FAILURE,

  /** Loading screen shown when uploading DDK backup */
  LOST_APP_DELAY_NOTIFY_DDK_UPLOAD,

  /** Loading screen shown when uploading encrypted descriptor backups */
  LOST_APP_DELAY_NOTIFY_ENCRYPTED_DESCRIPTOR_UPLOAD,

  /** Error screen shown when an error was encountered uploading DDK backup */
  LOST_APP_DELAY_NOTIFY_DDK_UPLOAD_FAILURE,

  /** Loading screen while cancelling recovery during D+N. */
  LOST_APP_DELAY_NOTIFY_CANCELLATION,

  /** Error screen shown when canceling lost app D+N fails */
  LOST_APP_DELAY_NOTIFY_CANCELLATION_ERROR,

  /** Screen for customer to enter comms verification code */
  LOST_APP_DELAY_NOTIFY_VERIFICATION_ENTRY,

  /** Screen shown when attempting to start Lost App D&N but the server has
   * an existing recovery that must first be canceled */
  LOST_APP_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT,

  /** Screen shown when attempting to cancel an existing recovery during
   * Lost App D&N initiation */
  LOST_APP_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_LOADING,
}
