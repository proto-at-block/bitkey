package build.wallet.analytics.events.screen.id

enum class CloudEventTrackerScreenId : EventTrackerScreenId {
  /** Error screen shown when creating a cloud backup fails during the new account creation process */
  SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT,

  /**
   * Error screen shown when creating a cloud backup fails during the new account creation process,
   * but there may be a resolution.
   */
  SAVE_CLOUD_BACKUP_FAILURE_NEW_ACCOUNT_RECTIFIABLE,

  /** Loading screen shown when attempting to sign in (or fetch for iOS) cloud account to use for recovery */
  CLOUD_SIGN_IN_LOADING,

  /** Error screen shown when  */
  CLOUD_BACKUP_NOT_FOUND,

  /** Troubleshooting steps shown when iCloud backup is not found or could not log in */
  CLOUD_BACKUP_NOT_FOUND_TROUBLESHOOTING,

  /** Instructions shown to the customer to save a backup of their account in cloud storage */
  SAVE_CLOUD_BACKUP_INSTRUCTIONS,

  /** Error screen shown when iCloud or Google are not signed in / accessible when trying to save the backup  */
  SAVE_CLOUD_BACKUP_NOT_SIGNED_IN,

  /**
   * Loading screen shown when we are checking for existing cloud backup during the full account
   * backup creation process
   */
  SAVE_CLOUD_BACKUP_CHECK_FOR_EXISTING,

  /** Loading screen shown when we are completing saving to cloud backup */
  SAVE_CLOUD_BACKUP_LOADING,

  /** Failed to save cloud backup. */
  SAVE_CLOUD_BACKUP_FAILED,

  /** Loading screen shown when we are checking cloud account and backup availability */
  CHECKING_CLOUD_BACKUP_AVAILABILITY,

  /** Screen prompting the customer to recovery their account with cloud backup once it's been found  */
  CLOUD_BACKUP_FOUND,

  /** Loading screen shown when we are restoring the account from cloud backup */
  LOADING_RESTORING_FROM_CLOUD_BACKUP,

  /** Loading screen shown when we are restoring the account from a lite account cloud backup transparently during full account onboarding */
  LOADING_RESTORING_FROM_LITE_ACCOUNT_CLOUD_BACKUP_DURING_FULL_ACCOUNT_ONBOARDING,

  /** Error screen shown when attempting to sign in to cloud account to use for recovery  */
  CLOUD_NOT_SIGNED_IN,

  /** Error shown when restoring account from cloud backup fails */
  FAILURE_RESTORE_FROM_CLOUD_BACKUP,

  /** Error shown when failing transparently restore a lite account backup during full account onboarding */
  FAILURE_RESTORE_FROM_LITE_ACCOUNT_CLOUD_BACKUP_AFTER_ONBOARDING,

  /** Deleting full account because user has declined to overwrite full account cloud backup. */
  DELETING_FULL_ACCOUNT,

  /**
   * Failure while deleting full account because user has declined to overwrite full account cloud
   * backup.
   */
  FAILURE_DELETING_FULL_ACCOUNT,

  /** Show warning about existing full account cloud backup before overwriting. */
  OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_ONBOARDING,

  /** Error shown when accessing cloud backup fails but there may be a resolution. */
  ACCESS_CLOUD_BACKUP_FAILURE_RECTIFIABLE,

  /** Explanation for authenticating with cloud recovery key for social recovery */
  SOCIAL_RECOVERY_EXPLANATION,

  /** Authenticating with cloud recovery key for social recovery */
  CLOUD_RECOVERY_AUTHENTICATION,

  /** Attempting to rectify a rectifiable error (Google Drive) */
  RECTIFYING_CLOUD_ERROR,

  /** Loading screen shown when we are creating a cloud backup */
  CREATING_CLOUD_BACKUP,

  /** Loading screen shown when we are preparing to create a cloud backup */
  PREPARING_CLOUD_BACKUP,

  /** Loading screen shown when we are uploading a cloud backup */
  UPLOADING_CLOUD_BACKUP,

  /** Show warning about existing full account cloud backup before overwriting. */
  OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_BACKUP_REPAIR,

  /** Confirming to overwrite full account cloud backup.*/
  CONFIRM_OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_DURING_LITE_ACCOUNT_ONBOARDING,

  /** Screen for selecting which orphaned account to recover when multiple accounts are found */
  ORPHANED_ACCOUNT_SELECTION,

  /** Screen for selecting which backup to recover when multiple unencryptable backups are found in cloud storage */
  SELECT_ACCOUNT_BACKUP,

  /** Error screen shown when creating a cloud backup fails during backup repair flow */
  ERROR_CREATING_CLOUD_BACKUP,

  /** Screen showing there is a problem with the cloud backup that needs attention */
  CLOUD_BACKUP_PROBLEM,

  /** Warning screen shown before deleting current backup and creating a new account */
  WARNING_DELETING_CLOUD_BACKUP,
}
