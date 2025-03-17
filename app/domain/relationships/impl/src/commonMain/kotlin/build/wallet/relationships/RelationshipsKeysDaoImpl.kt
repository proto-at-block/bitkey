package build.wallet.relationships

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.SocRecKeyPurpose
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbTransactionError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull
import kotlin.reflect.KClass

@BitkeyInject(AppScope::class)
class RelationshipsKeysDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val appPrivateKeyDao: AppPrivateKeyDao,
) : RelationshipsKeysDao {
  override suspend fun <T : SocRecKey> getPublicKey(
    keyClass: KClass<T>,
  ): Result<PublicKey<T>, RelationshipsKeyError> =
    getPublicKey(SocRecKeyPurpose.fromKeyType(keyClass))

  override suspend fun <T : SocRecKey> getKeyWithPrivateMaterial(
    keyClass: KClass<T>,
  ): Result<AppKey<T>, RelationshipsKeyError> =
    coroutineBinding {
      val publicKey = getPublicKey<T>(SocRecKeyPurpose.fromKeyType(keyClass)).bind()
      val privateKey =
        appPrivateKeyDao.getAsymmetricPrivateKey(publicKey)
          .mapError { RelationshipsKeyError.UnableToRetrieveKey(it) }
          .toErrorIfNull { RelationshipsKeyError.NoPrivateKeyAvailable() }
          .bind()
      AppKey(
        publicKey = publicKey,
        privateKey = privateKey
      )
    }

  override suspend fun <T : SocRecKey> saveKey(
    key: AppKey<T>,
    keyClass: KClass<T>,
  ): Result<Unit, RelationshipsKeyError> {
    return saveKey(SocRecKeyPurpose.fromKeyType(keyClass), key)
  }

  private suspend fun <T : SocRecKey> saveKey(
    purpose: SocRecKeyPurpose,
    appKey: AppKey<T>,
  ) = coroutineBinding {
    val db = databaseProvider.database()

    appPrivateKeyDao.storeAsymmetricPrivateKey(
      publicKey = appKey.publicKey,
      privateKey = appKey.privateKey
    )
      .mapError { RelationshipsKeyError.UnableToPersistKey(it) }
      .bind()

    @Suppress("UNCHECKED_CAST")
    db.socRecKeysQueries
      .awaitTransactionWithResult {
        insertKey(purpose, appKey.publicKey as PublicKey<Nothing>)
      }
      .mapError { RelationshipsKeyError.UnableToPersistKey(it) }
      .bind()
  }

  private suspend fun <T : SocRecKey> getPublicKey(
    purpose: SocRecKeyPurpose,
  ): Result<PublicKey<T>, RelationshipsKeyError> {
    val db = databaseProvider.database()
    @Suppress("UNCHECKED_CAST")
    return db.socRecKeysQueries
      .awaitTransactionWithResult {
        getKeyByPurpose(purpose).executeAsOneOrNull()?.key as PublicKey<T>?
      }
      .mapError { RelationshipsKeyError.UnableToRetrieveKey(it) }
      .toErrorIfNull { RelationshipsKeyError.NoKeyAvailable() }
  }

  override suspend fun clear(): Result<Unit, DbTransactionError> {
    val db = databaseProvider.database()
    return db.socRecKeysQueries
      .awaitTransactionWithResult {
        val keys = getAll().executeAsList()
        deleteAll()
        keys
      }
      .map {
        it.forEach {
          appPrivateKeyDao.remove(it.key)
        }
      }
  }
}
