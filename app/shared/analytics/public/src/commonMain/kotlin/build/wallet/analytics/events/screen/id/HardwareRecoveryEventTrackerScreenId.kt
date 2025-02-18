package build.wallet.analytics.events.screen.id

enum class HardwareRecoveryEventTrackerScreenId : EventTrackerScreenId {
  /** Hardware recovery via D+N is in progress */
  LOST_HW_DELAY_NOTIFY_PENDING,

  /** Error screen shown when initiating lost hw fails */
  LOST_HW_DELAY_NOTIFY_INITIATION_ERROR,

  /** Instructions for HW recovery via D+N */
  LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS,

  /** Loading screen shown when authenticating with the server for hardware D+N */
  LOST_HW_DELAY_NOTIFY_INITIATION_AUTHENTICATING_WITH_F8E,

  /** Loading screen shown when authenticating with the server for hardware D+N */
  LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY,

  /** Screen asking the customer if they have a new device ready for HW recovery */
  LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY,

  /** Hardware recovery via D+N is ready to complete */
  LOST_HW_DELAY_NOTIFY_READY,

  /** Error loading ddk for recovery */
  LOST_HW_DELAY_NOTIFY_DDK_RECOVERY_DATA_LOADING_ERROR,

  /** Loading screen shown when rotating auth keys for hardware D+N */
  LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS,

  /** Loading screen shown when creating spending keys for hardware D+N */
  LOST_HW_DELAY_NOTIFY_CREATING_SPENDING_KEYS,

  /** Error screen shown when creating spending keys fails for lost hardware D+N */
  LOST_HW_DELAY_NOTIFY_CREATING_SPENDING_KEYS_ERROR,

  /** Failed to sync socrec relationships error */
  LOST_HW_DELAY_NOTIFY_TRUSTED_CONTACT_SYNC_ERROR,

  /** Loading screen shown when generating PSBTs for hardware D+N */
  LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS,

  /** Loading screen shown when broadcating sweep for hardware D+N */
  LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING,

  /** Success screen shown when sweep for hardware D+N succeeds */
  LOST_HW_DELAY_NOTIFY_SWEEP_SUCCESS,

  /** Failure screen shown when sweep for hardware D+N fails */
  LOST_HW_DELAY_NOTIFY_SWEEP_FAILED,

  /** Prompt to sign PSBTs for hardware D+N */
  LOST_HW_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT,

  /** Screen shown when the recovered account has a zero balance */
  LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE,

  /** Error screen shown when generating the sweep PSBTs fails for lost hardware D+N */
  LOST_HW_DELAY_NOTIFY_SWEEP_GENERATE_PSBTS_ERROR,

  /** Error screen shown when user navigates away from sweep */
  LOST_HW_DELAY_NOTIFY_SWEEP_EXITED,

  /** Error screen shown when an error was encountered uploading cloud backup */
  LOST_HW_DELAY_NOTIFY_BACKUP_UPLOAD_FAILURE,

  /** Loading screen shown when uploading DDK backup */
  LOST_HW_DELAY_NOTIFY_DDK_UPLOAD,

  /** Error screen shown when an error was encountered uploading DDK backup */
  LOST_HW_DELAY_NOTIFY_DDK_UPLOAD_FAILURE,

  /** Loading screen while cancelling recovery during D+N. */
  LOST_HW_DELAY_NOTIFY_CANCELLATION,

  /** Screen for customer to enter comms verification code */
  LOST_HW_DELAY_NOTIFY_VERIFICATION_ENTRY,

  /** Screen shown when attempting to start Lost Hardware D&N but the server has
   * an existing recovery that must first be canceled */
  LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT,

  /** Screen shown when attempting to cancel an existing recovery during
   * Lost Hardware D&N initiation */
  LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_LOADING,

  /** Error screen shown when canceling lost hw D+N fails */
  LOST_HW_DELAY_NOTIFY_CANCELLATION_ERROR,
}
