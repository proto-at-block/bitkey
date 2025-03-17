package build.wallet.relationships

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result
import kotlin.reflect.KClass

/**
 * A data access object for storing and retrieving [SocRecKey]s.
 */
interface RelationshipsKeysDao {
  /**
   * Retrieve a [SocRecKey] from the database without private key material.
   *
   * @param keyFactory the factory to create the [SocRecKey] concrete type from the [AppKey]
   * @param keyClass the type of the [SocRecKey] to retrieve
   */
  suspend fun <T : SocRecKey> getPublicKey(
    keyClass: KClass<T>,
  ): Result<PublicKey<T>, RelationshipsKeyError>

  /**
   * Retrieve a [SocRecKey] along with its private key material.
   */
  suspend fun <T : SocRecKey> getKeyWithPrivateMaterial(
    keyClass: KClass<T>,
  ): Result<AppKey<T>, RelationshipsKeyError>

  /**
   * Save a [SocRecKey] to the database. If the key contains a [PrivateKey], it will be stored
   * to the encrypted store.
   */
  suspend fun <T : SocRecKey> saveKey(
    key: AppKey<T>,
    keyClass: KClass<T>,
  ): Result<Unit, RelationshipsKeyError>

  /**
   * Clear all public and private keys for SocRec from the database.
   */
  suspend fun clear(): Result<Unit, Error>
}

/**
 * Retrieve a [SocRecKey] from the database without private key material.
 *
 * @param keyFactory the factory to create the [SocRecKey] concrete type from the [AppKey]
 */
suspend inline fun <reified T : SocRecKey> RelationshipsKeysDao.getPublicKey(): Result<PublicKey<T>, RelationshipsKeyError> =
  getPublicKey(T::class)

/**
 * Retrieve a [SocRecKey] with its private key material.
 */
suspend inline fun <reified T : SocRecKey> RelationshipsKeysDao.getKeyWithPrivateMaterial(): Result<AppKey<T>, RelationshipsKeyError> =
  getKeyWithPrivateMaterial(T::class)

/**
 * Save a [SocRecKey] to the database. If the key contains a [PrivateKey], it will be stored
 * to the encrypted store.
 */
suspend inline fun <reified T : SocRecKey> RelationshipsKeysDao.saveKey(
  key: AppKey<T>,
): Result<Unit, RelationshipsKeyError> = saveKey(key, T::class)
