package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.ensure
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
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
 * Collect private app spending keys for [Keybox.keysets] into a map to later be encrypted in a backup.
 * This includes spending keys from all keysets (both active and inactive), not just the active one,
 * although there is no guarantee that inactive public keys will have a corresponding private key.
 */
internal suspend fun Keybox.appKeys(
  appPrivateKeyDao: AppPrivateKeyDao,
): Result<Map<AppSpendingPublicKey, AppSpendingPrivateKey>, Throwable> =
  coroutineBinding {
    val resultMap = linkedMapOf<AppSpendingPublicKey, AppSpendingPrivateKey>()

    // Get all available private keys from the DAO for logging purposes; don't bind the failure
    val allPrivateKeyPairsSize = appPrivateKeyDao.getAllAppSpendingKeyPairs()
      .logFailure { "Error reading all private spending app keys" }
      .map { it.size }
      .getOrElse { -1 }

    // Use unique keysets based on f8eSpendingKeyset.keysetId
    val uniqueKeysets = keysets.distinctBy { it.f8eSpendingKeyset.keysetId }

    logInfo {
      "[cloud_backup_creation] keybox.keysets.size: ${keysets.size}, " +
        "uniqueKeysets.size: ${uniqueKeysets.size}, allPrivateKeyPairs.size: $allPrivateKeyPairsSize, " +
        "keybox.canUseKeyboxKeysets: $canUseKeyboxKeysets"
    }

    // Retrieve each spending private key. Inactive keyset private keys may have been lost and that's ok.
    uniqueKeysets
      .map { it.appKey }
      .sortedBy { it.key.dpub }
      .forEach { spendingPublicKey ->
        appPrivateKeyDao
          .getAppSpendingPrivateKey(publicKey = spendingPublicKey)
          .onSuccess { privateKey ->
            if (privateKey != null) {
              resultMap[spendingPublicKey] = privateKey
            } else {
              logInfo { "Missing private spending key for spending public key: $spendingPublicKey" }
            }
          }
          .logFailure { "Error reading private spending app key" }
          .bind()
      }

    // We MUST have the active keyset private key
    ensure(resultMap.containsKey(activeSpendingKeyset.appKey)) {
      IllegalStateException("Active app spending private key not found.")
    }

    resultMap
  }
