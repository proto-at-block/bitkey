package build.wallet.analytics.events.screen.id

enum class InactiveWalletSweepEventTrackerScreenId : EventTrackerScreenId {
  /** Prompt to sign PSBTs for sweep */
  INACTIVE_WALLET_SWEEP_SIGN_PSBTS_PROMPT,

  /** Loading screen shown when generating PSBTs for sweep */
  INACTIVE_WALLET_SWEEP_GENERATING_PSBTS,

  /** Error screen shown when generating the sweep PSBTs fails for sweep */
  INACTIVE_WALLET_SWEEP_GENERATE_PSBTS_ERROR,

  /** Screen shown when the recovered account has a zero balance */
  INACTIVE_WALLET_SWEEP_ZERO_BALANCE,

  /** Loading screen shown when broadcating sweep */
  INACTIVE_WALLET_SWEEP_BROADCASTING,

  /** Success screen shown when sweep succeeds */
  INACTIVE_WALLET_SWEEP_SUCCESS,

  /** Failure screen shown when sweep fails */
  INACTIVE_WALLET_SWEEP_FAILED,

  /** Help screen explaining why the wallet requires a sweep */
  INACTIVE_WALLET_HELP,

  /** Warning screen shown on W3 when there are multiple transactions to sign */
  INACTIVE_WALLET_SWEEP_MULTIPLE_TRANSACTIONS_WARNING,

  /** Screen shown while awaiting hardware verification before sweep */
  INACTIVE_WALLET_SWEEP_HARDWARE_VERIFICATION_REQUIRED,

  /** Loading screen shown while generating an App Key Wallet sweep PSBT. */
  APP_KEY_WALLET_SWEEP_GENERATING_PSBT,

  /** Prompt to review and confirm the App Key Wallet sweep. */
  APP_KEY_WALLET_SWEEP_SIGN_PSBT_PROMPT,

  /** Loading screen shown while broadcasting the App Key Wallet sweep. */
  APP_KEY_WALLET_SWEEP_BROADCASTING,

  /** Success screen shown after the App Key Wallet sweep is broadcast. */
  APP_KEY_WALLET_SWEEP_SUCCESS,

  /** Failure screen shown when the App Key Wallet sweep cannot be completed. */
  APP_KEY_WALLET_SWEEP_FAILED,
}
