package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.logging.*
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.toErrorIfNull

/** Get [appGlobalAuthKeypair]. */
internal suspend fun Keybox.appGlobalAuthKeypair(
  appPrivateKeyDao: AppPrivateKeyDao,
): Result<AppKey<AppGlobalAuthKey>, Throwable> {
  val appAuthPublicKey = activeAppKeyBundle.authKey
  return appPrivateKeyDao
    .getAsymmetricPrivateKey(appAuthPublicKey)
    .toErrorIfNull { IllegalStateException("Active global app auth private key not found.") }
    .map { appAuthPrivateKey -> AppKey(appAuthPublicKey, appAuthPrivateKey) }
}

internal suspend fun Keybox.appRecoveryAuthKeypair(
  appPrivateKeyDao: AppPrivateKeyDao,
): Result<AppKey<AppRecoveryAuthKey>, Throwable> {
  val appAuthPublicKey = activeAppKeyBundle.recoveryAuthKey
  return appPrivateKeyDao
    .getAsymmetricPrivateKey(appAuthPublicKey)
    .toErrorIfNull { IllegalStateException("Active recovery app auth private key not found.") }
    .map { appAuthPrivateKey -> AppKey(appAuthPublicKey, appAuthPrivateKey) }
}

/** Collect all private app spending keys into a map for later to be encrypted in a backup. */
internal suspend fun Keybox.appKeys(
  appPrivateKeyDao: AppPrivateKeyDao,
): Result<Map<AppSpendingPublicKey, AppSpendingPrivateKey>, Throwable> =
  coroutineBinding {
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
              logWarn {
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
