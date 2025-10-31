package build.wallet.analytics.events.screen.id

enum class CreateAccountEventTrackerScreenId : EventTrackerScreenId {
  /** Loading screen shown when the server is generating keys for a new wallet */
  NEW_ACCOUNT_SERVER_KEYS_LOADING,

  /** Loading screen shown when the app is uploading descriptor backups to the Server */
  NEW_ACCOUNT_DESCRIPTOR_BACKUP_LOADING,

  /** Error screen shown when we failed to upload the descriptor backup during onboarding */
  NEW_ACCOUNT_DESCRIPTOR_BACKUP_FAILURE,

  /** The customer encountered a generic server error when trying to create a new account. */
  NEW_ACCOUNT_CREATION_FAILURE,

  /** The customer encountered an error when trying to create a new account because the hw was already paired. */
  NEW_ACCOUNT_CREATION_FAILURE_HW_KEY_ALREADY_IN_USE,

  /** The customer encountered an error when trying to create a new account because the hw was already paired. */
  NEW_ACCOUNT_CREATION_FAILURE_APP_KEY_ALREADY_IN_USE,

  /** The customer encountered a generic hardware error when trying to create a new account. */
  NEW_ACCOUNT_CREATION_HW_FAILURE,

  /** Error screen shown when we failed to generate App Keys. Should rarely, if ever, happen. */
  APP_KEYS_CREATION_FAILURE,

  /** Loading screen shown when we are determining which onboarding step to show. */
  LOADING_ONBOARDING_STEP,

  /** Loading screen shown when we are generating keys and the server is creating the lite account */
  NEW_LITE_ACCOUNT_CREATION,

  /** Error screen shown when we failed to create the lite account */
  NEW_LITE_ACCOUNT_CREATION_FAILURE,

  /** Errpr screen shown when we failed to upload the lite account backup */
  NEW_LITE_ACCOUNT_BACKUP_FAILURE,
}
