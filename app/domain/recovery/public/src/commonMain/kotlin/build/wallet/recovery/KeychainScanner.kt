package build.wallet.recovery

import com.github.michaelbull.result.Result
import dev.zacsweers.redacted.annotations.Redacted

/**
 * Scans iOS Keychain for orphaned private keys persisting after app deletion.
 *
 * Provides read-only access to encrypted keychain entries for emergency recovery scenarios.
 *
 * **Platform Support**: iOS only. Android implementation returns empty list as keychain entries
 * are automatically cleared on app uninstall. iOS Keychain persists data after uninstall unless
 * explicitly deleted by the user via Settings.
 */
interface KeychainScanner {
  /**
   * Scans the app's private key store in the iOS Keychain for all stored entries.
   *
   * Retrieves all key-value pairs stored under [build.wallet.bitcoin.AppPrivateKeyDao.STORE_NAME]
   * service name, including spending keys (xprv, mnemonic) and authentication keys.
   *
   * @return List of [KeychainEntry] containing all stored keys, or empty list if none found.
   */
  suspend fun scanAppPrivateKeyStore(): Result<List<KeychainEntry>, Throwable>

  /**
   * Represents a single key-value pair from the iOS Keychain.
   *
   * [Redacted] annotation prevents sensitive data from appearing in logs and crash reports.
   */
  @Redacted
  data class KeychainEntry(
    val key: String,
    val value: String,
  )
}
