package build.wallet.recovery.socrec

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.keys.app.AppKeyImpl
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.SocRecKeyPurpose
import build.wallet.crypto.CurveType
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
  override suspend fun <T : SocRecKey> getKey(
    keyFactory: (AppKey) -> T,
    keyClass: KClass<T>,
  ) = getPublicKey(SocRecKeyPurpose.fromKeyType(keyClass))
    .map {
      keyFactory(
        AppKeyImpl(
          curveType = CurveType.SECP256K1,
          publicKey = it,
          privateKey = null
        )
      )
    }

  override suspend fun <T : SocRecKey> getKeyWithPrivateMaterial(
    keyFactory: (AppKey) -> T,
    keyClass: KClass<T>,
  ): Result<T, SocRecKeyError> =
    binding {
      val publicKey = getPublicKey(SocRecKeyPurpose.fromKeyType(keyClass)).bind()
      val privateKey =
        appPrivateKeyDao.getAsymmetricPrivateKey(publicKey)
          .mapError { SocRecKeyError.UnableToRetrieveKey(it) }
          .toErrorIfNull { SocRecKeyError.NoPrivateKeyAvailable() }
          .bind()
      keyFactory(
        AppKeyImpl(
          curveType = CurveType.SECP256K1,
          publicKey = publicKey,
          privateKey = privateKey
        )
      )
    }

  override suspend fun saveKey(key: SocRecKey): Result<Unit, SocRecKeyError> {
    return saveKey(SocRecKeyPurpose.fromKeyType(key::class), key)
  }

  private suspend fun saveKey(
    purpose: SocRecKeyPurpose,
    key: SocRecKey,
  ) = binding {
    val appKey = key.key as AppKeyImpl
    val db = databaseProvider.database()

    if (appKey.privateKey != null) {
      appPrivateKeyDao.storeAsymmetricPrivateKey(
        publicKey = key.publicKey,
        privateKey = appKey.privateKey!!
      )
        .mapError { SocRecKeyError.UnableToPersistKey(it) }
        .bind()
    }

    db.socRecKeysQueries
      .awaitTransactionWithResult {
        insertKey(purpose, key.publicKey)
      }
      .mapError { SocRecKeyError.UnableToPersistKey(it) }
      .bind()
  }

  private suspend fun getPublicKey(purpose: SocRecKeyPurpose): Result<PublicKey, SocRecKeyError> {
    val db = databaseProvider.database()
    return db.socRecKeysQueries
      .awaitTransactionWithResult {
        getKeyByPurpose(purpose).executeAsOneOrNull()?.key
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
