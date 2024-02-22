package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppRecoveryAuthKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.app.requireRecoveryAuthKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.toErrorIfNull

/** Get [appGlobalAuthKeypair]. */
internal suspend fun Keybox.appGlobalAuthKeypair(
  appPrivateKeyDao: AppPrivateKeyDao,
): Result<AppGlobalAuthKeypair, Throwable> {
  val appAuthPublicKey = activeKeyBundle.authKey
  return appPrivateKeyDao
    .getGlobalAuthKey(appAuthPublicKey)
    .toErrorIfNull { IllegalStateException("Active global app auth private key not found.") }
    .map { appAuthPrivateKey -> AppGlobalAuthKeypair(appAuthPublicKey, appAuthPrivateKey) }
}

internal suspend fun Keybox.appRecoveryAuthKeypair(
  appPrivateKeyDao: AppPrivateKeyDao,
): Result<AppRecoveryAuthKeypair, Throwable> {
  // TODO: BKR-573 - backfill recovery key for existing customers.
  val appAuthPublicKey = activeKeyBundle.requireRecoveryAuthKey()
  return appPrivateKeyDao
    .getRecoveryAuthKey(appAuthPublicKey)
    .toErrorIfNull { IllegalStateException("Active recovery app auth private key not found.") }
    .map { appAuthPrivateKey -> AppRecoveryAuthKeypair(appAuthPublicKey, appAuthPrivateKey) }
}

/** Collect all private app spending keys into a map for later to be encrypted in a backup. */
internal suspend fun Keybox.appKeys(
  appPrivateKeyDao: AppPrivateKeyDao,
): Result<Map<AppSpendingPublicKey, AppSpendingPrivateKey>, Throwable> =
  binding {
    buildMap {
      // Retrieve active app spending key
      appPrivateKeyDao
        .getAppSpendingPrivateKey(publicKey = activeSpendingKeyset.appKey)
        .toErrorIfNull { IllegalStateException("Active app spending private key not found.") }
        .logFailure { "Error getting spending private key" }
        .onSuccess { activeSpendingExtendedPrivateKey ->
          put(activeSpendingKeyset.appKey, activeSpendingExtendedPrivateKey)
        }
        .bind()

      // Retrieve inactive app spending keys
      inactiveKeysets
        .onEach { inactiveKeySet ->
          appPrivateKeyDao
            .getAppSpendingPrivateKey(publicKey = inactiveKeySet.appKey)
            .onFailure {
              log(LogLevel.Info) {
                "Missing private spending key for inactive spending public key: ${inactiveKeySet.appKey}"
              }
            }
            .onSuccess { inactiveSpendingExtendedPrivateKey ->
              if (inactiveSpendingExtendedPrivateKey != null) {
                put(inactiveKeySet.appKey, inactiveSpendingExtendedPrivateKey)
              }
            }
            .logFailure { "Error reading inactive, private spending app key" }
            .bind()
        }
    }
  }
