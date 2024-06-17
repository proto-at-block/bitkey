package build.wallet.recovery.socrec

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentPakeKey
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbTransactionError
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.toErrorIfNull

class SocRecEnrollmentAuthenticationDaoImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  databaseProvider: BitkeyDatabaseProvider,
) : SocRecEnrollmentAuthenticationDao {
  private val database by lazy { databaseProvider.database() }

  override suspend fun insert(
    recoveryRelationshipId: String,
    protectedCustomerEnrollmentPakeKey: AppKey<ProtectedCustomerEnrollmentPakeKey>,
    pakeCode: PakeCode,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      appPrivateKeyDao.storeAsymmetricPrivateKey(
        protectedCustomerEnrollmentPakeKey.publicKey,
        requireNotNull(protectedCustomerEnrollmentPakeKey.privateKey)
      ).bind()
      database.awaitTransactionWithResult {
        database.socRecEnrollmentAuthenticationQueries.insert(
          recoveryRelationshipId,
          protectedCustomerEnrollmentPakeKey.publicKey,
          pakeCode.bytes
        )
      }.bind()
    }

  override suspend fun getByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<SocRecEnrollmentAuthenticationDao.SocRecEnrollmentAuthenticationRow?, Throwable> =
    coroutineBinding<SocRecEnrollmentAuthenticationDao.SocRecEnrollmentAuthenticationRow?, Throwable> {
      val auth =
        database.awaitTransactionWithResult {
          database.socRecEnrollmentAuthenticationQueries.getByRelationshipId(
            recoveryRelationshipId
          ).executeAsOneOrNull()
        }.bind()
      if (auth == null) {
        return@coroutineBinding Ok(null).bind()
      }
      val privateKey =
        appPrivateKeyDao.getAsymmetricPrivateKey(auth.protectedCustomerEnrollmentPakeKey)
          .toErrorIfNull {
            SocRecKeyError.NoPrivateKeyAvailable(
              message = "Protected customer enrollment private key missing"
            )
          }
          .bind()
      SocRecEnrollmentAuthenticationDao.SocRecEnrollmentAuthenticationRow(
        recoveryRelationshipId = auth.recoveryRelationshipId,
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
    database.awaitTransactionWithResult {
      database.socRecEnrollmentAuthenticationQueries.deleteByRelationshipId(
        recoveryRelationshipId
      )
    }

  override suspend fun clear(): Result<Unit, DbTransactionError> =
    database.awaitTransactionWithResult {
      database.socRecEnrollmentAuthenticationQueries.clear()
    }
}
