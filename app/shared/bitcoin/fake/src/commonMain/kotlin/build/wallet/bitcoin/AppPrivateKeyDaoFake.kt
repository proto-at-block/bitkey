package build.wallet.bitcoin

import build.wallet.bitkey.app.AppAuthKeypair
import build.wallet.bitkey.app.AppAuthPrivateKey
import build.wallet.bitkey.app.AppAuthPublicKey
import build.wallet.bitkey.app.AppGlobalAuthPrivateKey
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthPrivateKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.catching
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * Fake implementation of [AppPrivateKeyDao] baked by in memory storage.
 */
class AppPrivateKeyDaoFake : AppPrivateKeyDao {
  val appSpendingKeys = mutableMapOf<AppSpendingPublicKey, AppSpendingPrivateKey>()
  val appAuthKeys = mutableMapOf<AppAuthPublicKey, AppAuthPrivateKey>()
  val asymmetricKeys = mutableMapOf<PublicKey, PrivateKey>()
  var storeAppSpendingKeyPairResult: Result<Unit, Throwable> = Ok(Unit)
  var storeAppAuthKeyPairResult: Result<Unit, Throwable> = Ok(Unit)
  var getAppSpendingPrivateKeyErrResult: Err<Throwable>? = null
  var getAppAuthPrivateKeyErrResult: Err<Throwable>? = null

  override suspend fun storeAppSpendingKeyPair(
    keyPair: AppSpendingKeypair,
  ): Result<Unit, Throwable> {
    if (storeAppSpendingKeyPairResult !is Err) {
      appSpendingKeys[keyPair.publicKey] = keyPair.privateKey
    }
    return storeAppSpendingKeyPairResult
  }

  override suspend fun storeAppAuthKeyPair(keyPair: AppAuthKeypair): Result<Unit, Throwable> {
    if (storeAppAuthKeyPairResult !is Err) {
      appAuthKeys[keyPair.publicKey] = keyPair.privateKey
    }
    return storeAppAuthKeyPairResult
  }

  override suspend fun storeAsymmetricPrivateKey(
    publicKey: PublicKey,
    privateKey: PrivateKey,
  ): Result<Unit, Throwable> {
    return Result.catching {
      asymmetricKeys[publicKey] = privateKey
    }
  }

  override suspend fun getAppSpendingPrivateKey(
    publicKey: AppSpendingPublicKey,
  ): Result<AppSpendingPrivateKey?, Throwable> =
    getAppSpendingPrivateKeyErrResult ?: Ok(appSpendingKeys[publicKey])

  override suspend fun getGlobalAuthKey(
    publicKey: AppGlobalAuthPublicKey,
  ): Result<AppGlobalAuthPrivateKey?, Throwable> =
    getAppAuthPrivateKeyErrResult ?: Ok(appAuthKeys[publicKey] as? AppGlobalAuthPrivateKey)

  override suspend fun getRecoveryAuthKey(
    publicKey: AppRecoveryAuthPublicKey,
  ): Result<AppRecoveryAuthPrivateKey?, Throwable> =
    Ok(
      appAuthKeys[publicKey] as? AppRecoveryAuthPrivateKey
    )

  override suspend fun getAsymmetricPrivateKey(key: PublicKey): Result<PrivateKey?, Throwable> {
    return Ok(asymmetricKeys[key])
  }

  override suspend fun remove(key: AppSpendingPublicKey): Result<Unit, Throwable> {
    appSpendingKeys.remove(key)
    return Ok(Unit)
  }

  override suspend fun remove(key: AppAuthPublicKey): Result<Unit, Throwable> {
    appAuthKeys.remove(key)
    return Ok(Unit)
  }

  override suspend fun remove(key: PublicKey): Result<Unit, Throwable> {
    asymmetricKeys.remove(key)
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    appSpendingKeys.clear()
    appAuthKeys.clear()
    return Ok(Unit)
  }

  fun reset() {
    appSpendingKeys.clear()
    appAuthKeys.clear()
    storeAppSpendingKeyPairResult = Ok(Unit)
    storeAppAuthKeyPairResult = Ok(Unit)
    getAppSpendingPrivateKeyErrResult = null
    getAppAuthPrivateKeyErrResult = null
  }
}
