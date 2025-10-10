package build.wallet.recovery

import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

/**
 * Recovers wallet access from orphaned iOS Keychain entries after app deletion.
 *
 * Orchestrates emergency recovery by authenticating with F8e using orphaned auth keys,
 * fetching account keysets, and reconstructing a functional [Keybox] for wallet access.
 */
interface OrphanedKeyRecoveryService {
  /**
   * Represents a recoverable account found in orphaned keychain entries.
   *
   * @property accountId The F8e account identifier.
   * @property globalAuthKey The global authentication key for this account, if found.
   * @property recoveryAuthKey The recovery authentication key for this account, if found.
   * @property matchingKeysets Keysets from F8e that have matching spending keys in keychain.
   * @property allKeysets All keysets from F8e for this account (including non-matching ones).
   * @property balance The current Bitcoin balance, or null if balance fetch failed.
   * @property f8eEnvironment The F8e environment this account is registered with.
   * @property sourceKeychainEntries The original keychain entries containing private keys for recovery.
   */
  data class RecoverableAccount(
    val accountId: FullAccountId,
    val globalAuthKey: PublicKey<AppGlobalAuthKey>?,
    val recoveryAuthKey: PublicKey<AppRecoveryAuthKey>?,
    val matchingKeysets: List<SpendingKeyset>,
    val allKeysets: List<SpendingKeyset>,
    val balance: BitcoinBalance?,
    val f8eEnvironment: F8eEnvironment,
    val sourceKeychainEntries: List<KeychainScanner.KeychainEntry>,
  )

  /**
   * Checks whether orphaned keychain entries contain all required components
   * for recovery without performing any side effects (no F8e calls or key imports).
   *
   * Required components:
   * - At least one auth key pair (public + private key)
   * - At least one complete spending key (with both xprv and mnemonic)
   *
   * @param orphanedKeys List of keychain entries to validate.
   * @return `true` if all required components are present, `false` otherwise.
   */
  suspend fun canAttemptRecovery(orphanedKeys: List<KeychainScanner.KeychainEntry>): Boolean

  /**
   * Discovers all recoverable accounts from orphaned keychain entries.
   *
   * Uses orphaned keys detected by [OrphanedKeyDetectionService]. Validates each
   * auth key by attempting authentication with F8e, fetches keysets for authenticated
   * accounts, and attempts to fetch balance information. Returns only accounts that
   * have matching spending keys in the keychain.
   *
   * Discovery process:
   * 1. Parse keychain entries to extract auth and spending keys
   * 2. For each auth key, attempt F8e authentication
   * 3. Fetch keysets from F8e for authenticated accounts
   * 4. Match keysets with available spending keys (xprv + mnemonic)
   * 5. Attempt to fetch balance for each recoverable account
   * 6. Deduplicate by account ID
   *
   * @return List of [RecoverableAccount] on success, [RecoveryError] if no accounts found.
   */
  suspend fun discoverRecoverableAccounts(): Result<List<RecoverableAccount>, RecoveryError>

  /**
   * Recovers a functional [Keybox] from a pre-discovered [RecoverableAccount].
   *
   * This is an optimized recovery path that skips redundant F8e authentication and
   * keyset fetching since that work was already done during account discovery.
   *
   * Recovery process:
   * 1. Verify no existing keybox exists
   * 2. Import authentication private key from source keychain entries
   * 3. Import spending key material (xprv + mnemonic) from source keychain entries
   * 4. Reconstruct [Keybox] using pre-validated account data
   *
   * @param account Pre-discovered recoverable account with validated keysets and keys.
   * @return [Keybox] on successful recovery, [RecoveryError] on failure.
   */
  suspend fun recoverFromRecoverableAccount(
    account: RecoverableAccount,
  ): Result<Keybox, RecoveryError>

  /**
   * Errors that can occur during orphaned key recovery.
   */
  sealed interface RecoveryError {
    /**
     * Active or onboarding keybox already exists.
     * Recovery is only allowed when no keybox exists to prevent data loss.
     */
    data object KeyboxAlreadyExists : RecoveryError

    /**
     * No valid authentication key found in keychain entries.
     * Recovery cannot proceed without auth key for F8e authentication.
     */
    data object NoAuthKeyFound : RecoveryError

    /**
     * F8e authentication failed using discovered auth key.
     *
     * @property cause Underlying exception from authentication attempt.
     */
    data class AuthenticationFailed(val cause: Throwable?) : RecoveryError

    /**
     * Failed to fetch keysets from F8e after successful authentication.
     *
     * @property cause Underlying exception from F8e keyset fetch.
     */
    data class F8eFetchFailed(val cause: Throwable?) : RecoveryError

    /**
     * Failed to import recovered keys back into secure storage.
     *
     * @property cause Underlying exception from key import operation.
     */
    data class KeyImportFailed(val cause: Throwable?) : RecoveryError

    /**
     * Failed to match orphaned keys with F8e keyset or construct valid key material.
     *
     * @property reason Human-readable explanation of reconstruction failure.
     */
    data class KeyReconstructionFailed(val reason: String) : RecoveryError
  }
}
