package build.wallet.relationships

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.PakeCode
import build.wallet.bitkey.relationships.ProtectedCustomerEnrollmentPakeKey
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbTransactionError
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.toErrorIfNull

class RelationshipsEnrollmentAuthenticationDaoImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val databaseProvider: BitkeyDatabaseProvider,
) : RelationshipsEnrollmentAuthenticationDao {
  override suspend fun insert(
    recoveryRelationshipId: String,
    protectedCustomerEnrollmentPakeKey: AppKey<ProtectedCustomerEnrollmentPakeKey>,
    pakeCode: PakeCode,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      appPrivateKeyDao.storeAsymmetricPrivateKey(
        protectedCustomerEnrollmentPakeKey.publicKey,
        protectedCustomerEnrollmentPakeKey.privateKey
      ).bind()
      databaseProvider.database().awaitTransactionWithResult {
        socRecEnrollmentAuthenticationQueries.insert(
          recoveryRelationshipId,
          protectedCustomerEnrollmentPakeKey.publicKey,
          pakeCode.bytes
        )
      }.bind()
    }

  override suspend fun getByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<RelationshipsEnrollmentAuthenticationDao.RelationshipsEnrollmentAuthenticationRow?, Throwable> =
    coroutineBinding {
      val auth =
        databaseProvider.database().awaitTransactionWithResult {
          socRecEnrollmentAuthenticationQueries.getByRelationshipId(
            recoveryRelationshipId
          ).executeAsOneOrNull()
        }.bind()
      if (auth == null) {
        return@coroutineBinding Ok(null).bind()
      }
      val privateKey =
        appPrivateKeyDao.getAsymmetricPrivateKey(auth.protectedCustomerEnrollmentPakeKey)
          .toErrorIfNull {
            RelationshipsKeyError.NoPrivateKeyAvailable(
              message = "Protected customer enrollment private key missing"
            )
          }
          .bind()
      RelationshipsEnrollmentAuthenticationDao.RelationshipsEnrollmentAuthenticationRow(
        relationshipId = auth.recoveryRelationshipId,
        protectedCustomerEnrollmentPakeKey =
          AppKey(
            auth.protectedCustomerEnrollmentPakeKey,
            privateKey
          ),
        pakeCode = PakeCode(auth.pakeCode)
      )
    }

  override suspend fun deleteByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<Unit, DbTransactionError> =
    databaseProvider.database().awaitTransactionWithResult {
      socRecEnrollmentAuthenticationQueries.deleteByRelationshipId(
        recoveryRelationshipId
      )
    }

  override suspend fun clear(): Result<Unit, DbTransactionError> =
    databaseProvider.database().awaitTransactionWithResult {
      socRecEnrollmentAuthenticationQueries.clear()
    }
}
