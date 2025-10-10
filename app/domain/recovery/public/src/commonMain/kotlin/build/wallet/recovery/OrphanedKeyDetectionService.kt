package build.wallet.recovery

import kotlinx.coroutines.flow.StateFlow

/**
 * Detects orphaned private keys in the iOS Keychain after app deletion.
 *
 * Compares keychain entries against active keybox to identify keys persisting
 * after app uninstall but without corresponding app database records.
 *
 * **Platform Support**: iOS only. On Android, this service always returns
 * [OrphanedKeysState.NoOrphanedKeys] as the platform automatically clears
 * keystore entries on app uninstall.
 */
interface OrphanedKeyDetectionService {
  /**
   * Reactive state of orphaned keys detection.
   *
   * @return StateFlow emitting [OrphanedKeysState] whenever detection runs.
   */
  fun orphanedKeysState(): StateFlow<OrphanedKeysState>

  /**
   * Scans keychain for orphaned keys not associated with active keybox.
   *
   * Checks if an active keybox exists, then scans keychain entries. Keys are
   * considered orphaned if keychain entries exist but no active keybox is found.
   *
   * @return [OrphanedKeysState.NoOrphanedKeys] if active keybox exists or no entries found,
   *         [OrphanedKeysState.OrphanedKeysFound] with keychain entries otherwise.
   */
  suspend fun detect(): OrphanedKeysState
}

/**
 * Represents the state of orphaned key detection in the iOS Keychain.
 */
sealed class OrphanedKeysState {
  /**
   * No orphaned keys detected. Either active keybox exists or keychain is empty.
   */
  data object NoOrphanedKeys : OrphanedKeysState()

  /**
   * Orphaned keys found in keychain without corresponding active keybox.
   *
   * @property entries List of keychain entries that may be used for emergency recovery.
   */
  data class OrphanedKeysFound(
    val entries: List<KeychainScanner.KeychainEntry>,
  ) : OrphanedKeysState()
}
