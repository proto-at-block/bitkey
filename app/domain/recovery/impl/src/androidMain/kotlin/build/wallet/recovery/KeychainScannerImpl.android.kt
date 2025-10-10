package build.wallet.recovery

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * Android stub implementation of [KeychainScanner].
 *
 * Always returns empty list because Android Keystore entries are automatically cleared
 * when an app is uninstalled. Unlike iOS Keychain which persists data after app deletion,
 * Android does not support orphaned key recovery scenarios.
 *
 * Keychain persistence after app deletion is iOS-specific behavior.
 */
@BitkeyInject(AppScope::class)
class KeychainScannerImpl : KeychainScanner {
  override suspend fun scanAppPrivateKeyStore(): Result<List<KeychainScanner.KeychainEntry>, Throwable> =
    Ok(emptyList())
}
