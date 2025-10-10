package build.wallet.recovery

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logFailure
import build.wallet.recovery.OrphanedKeysState.NoOrphanedKeys
import build.wallet.recovery.OrphanedKeysState.OrphanedKeysFound
import com.github.michaelbull.result.getOrElse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@BitkeyInject(AppScope::class)
class OrphanedKeyDetectionServiceImpl(
  private val keychainScanner: KeychainScanner,
  private val keyboxDao: KeyboxDao,
) : OrphanedKeyDetectionService {
  private companion object {
    const val LOG_TAG = "[OrphanedKeyRecovery]"
  }

  private val _orphanedKeysState = MutableStateFlow<OrphanedKeysState>(NoOrphanedKeys)

  override fun orphanedKeysState(): StateFlow<OrphanedKeysState> = _orphanedKeysState.asStateFlow()

  override suspend fun detect(): OrphanedKeysState {
    val hasActiveKeybox =
      keyboxDao.getActiveOrOnboardingKeybox().getOrElse { null } != null

    if (hasActiveKeybox) {
      _orphanedKeysState.value = NoOrphanedKeys
      return NoOrphanedKeys
    }

    val keychainEntries =
      keychainScanner.scanAppPrivateKeyStore()
        .logFailure { "$LOG_TAG Failed to scan keychain for orphaned keys" }
        .getOrElse { emptyList() }

    val state =
      if (keychainEntries.isEmpty()) {
        NoOrphanedKeys
      } else {
        OrphanedKeysFound(keychainEntries)
      }

    _orphanedKeysState.value = state
    return state
  }
}
