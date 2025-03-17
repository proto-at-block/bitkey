package build.wallet.onboarding

import bitkey.account.AccountConfig
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeys
import build.wallet.bitkey.keybox.Keybox
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import com.github.michaelbull.result.Result

/**
 * Domain service for creating a new [FullAccount] during brand-new onboarding, or during
 * Lite account -> Full account upgrade path.
 *
 * The account creation process consists of:
 * - [createAppKeys] - this will create the app keys for the account, persist them locally on device and into
 *   pending onboarding state.
 * - [createAccount] - this will finish the account creation process by creating the account on the server.
 *   Requires hardware keys (from hardware fingerprint enrollment process, likely done by UI state machine).
 */
interface OnboardFullAccountService {
  /**
   * Creates app keys for a new [FullAccount]. Uses [AccountConfig] based on an ongoing
   * existing onboarding keys, otherwise default configuration or based on debug options (in team and dev builds).
   */
  suspend fun createAppKeys(): Result<WithAppKeys, Throwable>

  /**
   * Finishes full account creation process by creating the account on the server.
   * Requires hardware keys from fingerprint enrollment process.
   */
  suspend fun createAccount(
    context: CreateFullAccountContext,
    appKeys: WithAppKeys,
    hwActivation: FingerprintEnrolled,
  ): Result<FullAccount, Throwable>

  /**
   * Activates the account (associated with the provided [Keybox]). This effectively
   * completes onboarding and makes the account usable.
   *
   * TODO(W-9915):change API to accept [FullAccount] instead of [Keybox].
   */
  suspend fun activateAccount(keybox: Keybox): Result<Unit, Throwable>

  /**
   * Cancels account creation process. This will delete keys creating during the onboarding attempt.
   */
  suspend fun cancelAccountCreation(): Result<Unit, Throwable>
}
