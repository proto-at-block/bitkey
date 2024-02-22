package build.wallet.recovery.socrec

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keys.app.AppKeyImpl
import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentKey
import build.wallet.crypto.CurveType
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.database.sqldelight.SocRecEnrollmentAuthentication
import build.wallet.db.DbTransactionError
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.toErrorIfNull

class SocRecEnrollmentAuthenticationDaoImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  databaseProviderImpl: BitkeyDatabaseProviderImpl,
) : SocRecEnrollmentAuthenticationDao {
  private val database by lazy { databaseProviderImpl.database() }

  override suspend fun insert(
    recoveryRelationshipId: String,
    protectedCustomerEnrollmentKey: ProtectedCustomerEnrollmentKey,
    pakeCode: String,
  ): Result<Unit, Throwable> =
    binding {
      appPrivateKeyDao.storeAsymmetricPrivateKey(
        protectedCustomerEnrollmentKey.publicKey,
        requireNotNull((protectedCustomerEnrollmentKey.key as AppKeyImpl).privateKey)
      ).bind()
      database.awaitTransactionWithResult {
        database.socRecEnrollmentAuthenticationQueries.insert(
          recoveryRelationshipId,
          protectedCustomerEnrollmentKey,
          pakeCode
        )
      }.bind()
    }

  override suspend fun getByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<SocRecEnrollmentAuthentication?, Throwable> =
    binding {
      val auth =
        database.awaitTransactionWithResult {
          database.socRecEnrollmentAuthenticationQueries.getByRelationshipId(
            recoveryRelationshipId
          ).executeAsOneOrNull()
        }.bind()
      if (auth == null) {
        return@binding Ok(null).bind()
      }
      val privateKey =
        appPrivateKeyDao.getAsymmetricPrivateKey(auth.protectedCustomerEnrollmentKey.publicKey)
          .toErrorIfNull {
            SocRecKeyError.NoPrivateKeyAvailable(
              message = "Protected customer enrollment private key missing"
            )
          }
          .bind()
      auth.copy(
        protectedCustomerEnrollmentKey =
          ProtectedCustomerEnrollmentKey(
            AppKeyImpl(
              CurveType.Curve25519,
              auth.protectedCustomerEnrollmentKey.publicKey,
              privateKey
            )
          )
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
