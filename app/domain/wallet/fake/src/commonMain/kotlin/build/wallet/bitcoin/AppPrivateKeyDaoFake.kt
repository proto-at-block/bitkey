package build.wallet.bitcoin

import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.catchingResult
import build.wallet.crypto.KeyPurpose
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

/**
 * Fake implementation of [AppPrivateKeyDao] baked by in memory storage.
 */
@Suppress("UNCHECKED_CAST")
class AppPrivateKeyDaoFake : AppPrivateKeyDao {
  val appSpendingKeys = mutableMapOf<AppSpendingPublicKey, AppSpendingPrivateKey>()
  val asymmetricKeys = mutableMapOf<PublicKey<*>, PrivateKey<*>>()
  var storeAppSpendingKeyPairResult: Result<Unit, Throwable> = Ok(Unit)
  var storeAppAuthKeyPairResult: Result<Unit, Throwable> = Ok(Unit)
  var getAppSpendingPrivateKeyErrResult: Result<AppSpendingPrivateKey?, Throwable>? = null
  var getAppPrivateKeyErrResult: Result<PrivateKey<*>?, Throwable>? = null
  var getAllAppSpendingKeyPairs: Result<List<AppSpendingKeypair>, Throwable>? = null

  override suspend fun storeAppSpendingKeyPair(
    keyPair: AppSpendingKeypair,
  ): Result<Unit, Throwable> {
    if (storeAppSpendingKeyPairResult.isOk) {
      appSpendingKeys[keyPair.publicKey] = keyPair.privateKey
    }
    return storeAppSpendingKeyPairResult
  }

  override suspend fun <T : AppAuthKey> storeAppKeyPair(
    keyPair: AppKey<T>,
  ): Result<Unit, Throwable> {
    if (storeAppAuthKeyPairResult.isOk) {
      asymmetricKeys[keyPair.publicKey] = keyPair.privateKey
    }
    return storeAppAuthKeyPairResult
  }

  override suspend fun <T : KeyPurpose> storeAsymmetricPrivateKey(
    publicKey: PublicKey<T>,
    privateKey: PrivateKey<T>,
  ): Result<Unit, Throwable> {
    return catchingResult {
      asymmetricKeys[publicKey] = privateKey
    }
  }

  override suspend fun getAppSpendingPrivateKey(
    publicKey: AppSpendingPublicKey,
  ): Result<AppSpendingPrivateKey?, Throwable> =
    getAppSpendingPrivateKeyErrResult ?: Ok(appSpendingKeys[publicKey])

  override suspend fun getAllAppSpendingKeyPairs(): Result<List<AppSpendingKeypair>, Throwable> {
    return getAllAppSpendingKeyPairs ?: Ok(
      appSpendingKeys.map { (publicKey, privateKey) ->
        AppSpendingKeypair(privateKey = privateKey, publicKey = publicKey)
      }
    )
  }

  override suspend fun <T : KeyPurpose> getAsymmetricPrivateKey(
    key: PublicKey<T>,
  ): Result<PrivateKey<T>?, Throwable> {
    return getAppPrivateKeyErrResult
      ?.map { it as? PrivateKey<T> }
      ?: Ok(asymmetricKeys[key] as PrivateKey<T>?)
  }

  override suspend fun remove(key: AppSpendingPublicKey): Result<Unit, Throwable> {
    appSpendingKeys.remove(key)
    return Ok(Unit)
  }

  override suspend fun <T : KeyPurpose> remove(key: PublicKey<T>): Result<Unit, Throwable> {
    asymmetricKeys.remove(key)
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    appSpendingKeys.clear()
    asymmetricKeys.clear()
    return Ok(Unit)
  }

  fun reset() {
    appSpendingKeys.clear()
    asymmetricKeys.clear()
    storeAppSpendingKeyPairResult = Ok(Unit)
    storeAppAuthKeyPairResult = Ok(Unit)
    getAppSpendingPrivateKeyErrResult = null
    getAppPrivateKeyErrResult = null
  }
}
