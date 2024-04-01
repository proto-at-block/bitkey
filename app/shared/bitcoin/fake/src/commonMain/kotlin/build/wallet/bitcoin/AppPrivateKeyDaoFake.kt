package build.wallet.bitcoin

import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.catching
import build.wallet.crypto.KeyPurpose
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * Fake implementation of [AppPrivateKeyDao] baked by in memory storage.
 */
@Suppress("UNCHECKED_CAST")
class AppPrivateKeyDaoFake : AppPrivateKeyDao {
  val appSpendingKeys = mutableMapOf<AppSpendingPublicKey, AppSpendingPrivateKey>()
  val asymmetricKeys = mutableMapOf<PublicKey<*>, PrivateKey<*>>()
  var storeAppSpendingKeyPairResult: Result<Unit, Throwable> = Ok(Unit)
  var storeAppAuthKeyPairResult: Result<Unit, Throwable> = Ok(Unit)
  var getAppSpendingPrivateKeyErrResult: Err<Throwable>? = null
  var getAppPrivateKeyErrResult: Err<Throwable>? = null

  override suspend fun storeAppSpendingKeyPair(
    keyPair: AppSpendingKeypair,
  ): Result<Unit, Throwable> {
    if (storeAppSpendingKeyPairResult !is Err) {
      appSpendingKeys[keyPair.publicKey] = keyPair.privateKey
    }
    return storeAppSpendingKeyPairResult
  }

  override suspend fun <T : AppAuthKey> storeAppKeyPair(
    keyPair: AppKey<T>,
  ): Result<Unit, Throwable> {
    if (storeAppAuthKeyPairResult !is Err) {
      asymmetricKeys[keyPair.publicKey] = keyPair.privateKey
    }
    return storeAppAuthKeyPairResult
  }

  override suspend fun <T : KeyPurpose> storeAsymmetricPrivateKey(
    publicKey: PublicKey<T>,
    privateKey: PrivateKey<T>,
  ): Result<Unit, Throwable> {
    return Result.catching {
      asymmetricKeys[publicKey] = privateKey
    }
  }

  override suspend fun getAppSpendingPrivateKey(
    publicKey: AppSpendingPublicKey,
  ): Result<AppSpendingPrivateKey?, Throwable> =
    getAppSpendingPrivateKeyErrResult ?: Ok(appSpendingKeys[publicKey])

  override suspend fun <T : KeyPurpose> getAsymmetricPrivateKey(
    key: PublicKey<T>,
  ): Result<PrivateKey<T>?, Throwable> {
    return getAppPrivateKeyErrResult ?: Ok(asymmetricKeys[key] as PrivateKey<T>?)
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
