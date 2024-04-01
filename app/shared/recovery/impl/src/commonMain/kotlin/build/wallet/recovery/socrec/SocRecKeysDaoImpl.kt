package build.wallet.recovery.socrec

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.SocRecKeyPurpose
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbTransactionError
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull
import kotlin.reflect.KClass

class SocRecKeysDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val appPrivateKeyDao: AppPrivateKeyDao,
) : SocRecKeysDao {
  override suspend fun <T : SocRecKey> getPublicKey(
    keyClass: KClass<T>,
  ): Result<PublicKey<T>, SocRecKeyError> = getPublicKey(SocRecKeyPurpose.fromKeyType(keyClass))

  override suspend fun <T : SocRecKey> getKeyWithPrivateMaterial(
    keyClass: KClass<T>,
  ): Result<AppKey<T>, SocRecKeyError> =
    binding {
      val publicKey = getPublicKey<T>(SocRecKeyPurpose.fromKeyType(keyClass)).bind()
      val privateKey =
        appPrivateKeyDao.getAsymmetricPrivateKey(publicKey)
          .mapError { SocRecKeyError.UnableToRetrieveKey(it) }
          .toErrorIfNull { SocRecKeyError.NoPrivateKeyAvailable() }
          .bind()
      AppKey(
        publicKey = publicKey,
        privateKey = privateKey
      )
    }

  override suspend fun <T : SocRecKey> saveKey(
    key: AppKey<T>,
    keyClass: KClass<T>,
  ): Result<Unit, SocRecKeyError> {
    return saveKey(SocRecKeyPurpose.fromKeyType(keyClass), key)
  }

  private suspend fun <T : SocRecKey> saveKey(
    purpose: SocRecKeyPurpose,
    appKey: AppKey<T>,
  ) = binding {
    val db = databaseProvider.database()

    appPrivateKeyDao.storeAsymmetricPrivateKey(
      publicKey = appKey.publicKey,
      privateKey = appKey.privateKey
    )
      .mapError { SocRecKeyError.UnableToPersistKey(it) }
      .bind()

    @Suppress("UNCHECKED_CAST")
    db.socRecKeysQueries
      .awaitTransactionWithResult {
        insertKey(purpose, appKey.publicKey as PublicKey<Nothing>)
      }
      .mapError { SocRecKeyError.UnableToPersistKey(it) }
      .bind()
  }

  private suspend fun <T : SocRecKey> getPublicKey(
    purpose: SocRecKeyPurpose,
  ): Result<PublicKey<T>, SocRecKeyError> {
    val db = databaseProvider.database()
    @Suppress("UNCHECKED_CAST")
    return db.socRecKeysQueries
      .awaitTransactionWithResult {
        getKeyByPurpose(purpose).executeAsOneOrNull()?.key as PublicKey<T>?
      }
      .mapError { SocRecKeyError.UnableToRetrieveKey(it) }
      .toErrorIfNull { SocRecKeyError.NoKeyAvailable() }
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
