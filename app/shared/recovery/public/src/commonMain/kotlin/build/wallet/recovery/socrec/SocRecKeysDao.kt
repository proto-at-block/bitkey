package build.wallet.recovery.socrec

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.crypto.PrivateKey
import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Result
import kotlin.reflect.KClass

/**
 * A data access object for storing and retrieving [SocRecKey]s.
 */
interface SocRecKeysDao {
  /**
   * Retrieve a [SocRecKey] from the database without private key material.
   *
   * @param keyFactory the factory to create the [SocRecKey] concrete type from the [AppKey]
   * @param keyClass the type of the [SocRecKey] to retrieve
   */
  suspend fun <T : SocRecKey> getKey(
    keyFactory: (AppKey) -> T,
    keyClass: KClass<T>,
  ): Result<T, SocRecKeyError>

  /**
   * Retrieve a [SocRecKey] along with its private key material.
   */
  suspend fun <T : SocRecKey> getKeyWithPrivateMaterial(
    keyFactory: (AppKey) -> T,
    keyClass: KClass<T>,
  ): Result<T, SocRecKeyError>

  /**
   * Save a [SocRecKey] to the database. If the key contains a [PrivateKey], it will be stored
   * to the encrypted store.
   */
  suspend fun saveKey(key: SocRecKey): Result<Unit, SocRecKeyError>

  /**
   * Clear all public and private keys for SocRec from the database.
   */
  suspend fun clear(): Result<Unit, DbTransactionError>
}

/**
 * Retrieve a [SocRecKey] from the database without private key material.
 *
 * @param keyFactory the factory to create the [SocRecKey] concrete type from the [AppKey]
 */
suspend inline fun <reified T : SocRecKey> SocRecKeysDao.getKey(
  noinline keyFactory: (AppKey) -> T,
): Result<T, SocRecKeyError> = getKey(keyFactory, T::class)

/**
 * Retrieve a [SocRecKey] with its private key material.
 */
suspend inline fun <reified T : SocRecKey> SocRecKeysDao.getKeyWithPrivateMaterial(
  noinline keyFactory: (AppKey) -> T,
): Result<T, SocRecKeyError> = getKeyWithPrivateMaterial(keyFactory, T::class)
