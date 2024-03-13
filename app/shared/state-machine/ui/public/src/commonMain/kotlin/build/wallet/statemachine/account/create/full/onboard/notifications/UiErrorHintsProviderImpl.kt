package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.statemachine.notifications.UiErrorHintSubmitter
import build.wallet.store.EncryptedKeyValueStoreFactory
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Storage implementation. We're using an encrypted key store. The current use of this component
 * is to record that the user attempted to register a US-based phone number. While not exactly
 * "PII", it's not *entirely* OK, in the sense that you have a US phone number. If
 * this is OK unencrypted, swap out the standard key store.
 */
@OptIn(ExperimentalSettingsApi::class)
class UiErrorHintsProviderImpl(
  private val keystoreFactory: EncryptedKeyValueStoreFactory,
  private val appScope: CoroutineScope,
) : UiErrorHintsProvider, UiErrorHintSubmitter {
  // We *probably* only get here from the main thread, but better safe than sorry
  // Careful with this. It is *not* reentrant.
  private val mutex = Mutex()
  private var settings: SuspendSettings? = null
  private val hintStateFlows: Map<UiErrorHintKey, MutableStateFlow<UiErrorHint>> =
    UiErrorHintKey.entries.associateWith { key -> MutableStateFlow(UiErrorHint.None) }

  override suspend fun getErrorHint(key: UiErrorHintKey): UiErrorHint =
    mutex.withLock {
      nonLockingGetErrorHint(key)
    }

  override fun errorHintFlow(key: UiErrorHintKey): StateFlow<UiErrorHint> =
    (hintStateFlows[key] as? StateFlow<UiErrorHint>)
      ?: error("No state flow for \"$key\". This should be impossible")

  override suspend fun setErrorHint(
    key: UiErrorHintKey,
    hint: UiErrorHint,
  ) {
    mutex.withLock {
      nonLockingHintSettings().putString(key.name, hint.name)
      hintStateFlows[key]?.value = hint
    }
  }

  private suspend fun UiErrorHintsProviderImpl.nonLockingGetErrorHint(key: UiErrorHintKey) =
    UiErrorHint.valueOfOrNone(nonLockingHintSettings().getString(key.name, UiErrorHint.None.name))

  private suspend fun nonLockingHintSettings(): SuspendSettings {
    // There must be a better way to write this. Looking forward to the PR feedback :)
    val checkSettings = settings
    return if (checkSettings == null) {
      val s = keystoreFactory.getOrCreate("UI_ERROR_HINTS")
      settings = s
      s
    } else {
      checkSettings
    }
  }

  override fun phoneNone() {
    appScope.launch {
      setErrorHint(UiErrorHintKey.Phone, UiErrorHint.None)
    }
  }

  override fun phoneNotAvailable() {
    appScope.launch {
      setErrorHint(UiErrorHintKey.Phone, UiErrorHint.NotAvailableInYourCountry)
    }
  }

  init {
    appScope.launch {
      mutex.withLock {
        UiErrorHintKey.entries.forEach { key ->
          val errorHint = nonLockingGetErrorHint(key)
          hintStateFlows[key]?.value = errorHint
        }
      }
    }
  }
}
