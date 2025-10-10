package build.wallet.recovery

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * JVM stub implementation of [KeychainScanner].
 *
 * Keychain persistence after app deletion is iOS-specific behavior.
 * JVM target is used for testing and doesn't have keychain access.
 */
@BitkeyInject(AppScope::class)
class KeychainScannerImpl : KeychainScanner {
  override suspend fun scanAppPrivateKeyStore(): Result<List<KeychainScanner.KeychainEntry>, Throwable> =
    Ok(emptyList())
}
