package build.wallet.bitcoin.wallet

/**
 * State for a wallet's first local data sync after the app switches wallet implementations.
 */
sealed interface WalletInitialSyncStatus {
  /**
   * No first-sync gate is active for this wallet.
   */
  data object NotRequired : WalletInitialSyncStatus

  /**
   * Wallet history, balance, and UTXOs are being loaded before the wallet can be shown.
   */
  data object Syncing : WalletInitialSyncStatus

  /**
   * The first wallet data sync failed and needs to be retried.
   */
  data class Failed(val cause: Throwable) : WalletInitialSyncStatus
}
