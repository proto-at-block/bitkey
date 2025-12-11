package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.ensure
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding

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

/**
 * Collect all available private app spending keys into a map for later to be encrypted in a backup.
 * This includes spending keys from all keysets (both active and inactive), not just the active one,
 * although there is no guarantee that inactive public keys will have a corresponding private key.
 */
internal suspend fun Keybox.appKeys(
  appPrivateKeyDao: AppPrivateKeyDao,
): Result<Map<AppSpendingPublicKey, AppSpendingPrivateKey>, Throwable> =
  coroutineBinding {
    // Retrieve each spending private key, sorted by dpub for deterministic ordering.
    // This ensures consistent map iteration order for backup serialization/comparison.
    val resultMap = appPrivateKeyDao.getAllAppSpendingKeyPairs()
      .bind()
      .sortedBy { it.publicKey.key.dpub }
      .associate { it.publicKey to it.privateKey }

    // We MUST have the active keyset private key; inactive private keys may have been lost and
    // that's ok.
    ensure(resultMap.containsKey(activeSpendingKeyset.appKey)) {
      IllegalStateException("Active app spending private key not found.")
    }

    resultMap.toMap()
  }
