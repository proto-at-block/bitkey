package build.wallet.recovery.socrec

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keys.app.AppKeyImpl
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.crypto.CurveType
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.SocRecStartedChallengeAuthentication
import build.wallet.db.DbTransactionError
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.toErrorIfNull

class SocRecStartedChallengeAuthenticationDaoImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  databaseProviderImpl: BitkeyDatabaseProvider,
) : SocRecStartedChallengeAuthenticationDao {
  private val database by lazy { databaseProviderImpl.database() }

  override suspend fun insert(
    recoveryRelationshipId: String,
    protectedCustomerRecoveryPakeKey: ProtectedCustomerRecoveryPakeKey,
    pakeCode: PakeCode,
  ): Result<Unit, Throwable> =
    binding {
      appPrivateKeyDao.storeAsymmetricPrivateKey(
        protectedCustomerRecoveryPakeKey.publicKey,
        requireNotNull((protectedCustomerRecoveryPakeKey.key as AppKeyImpl).privateKey)
      ).bind()
      database.awaitTransactionWithResult {
        database.socRecStartedChallengeAuthenticationQueries.insert(
          relationshipId = recoveryRelationshipId,
          protectedCustomerRecoveryPakeKey = protectedCustomerRecoveryPakeKey,
          pakeCode = pakeCode.bytes
        )
      }.bind()
    }

  override suspend fun getByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<SocRecStartedChallengeAuthentication?, Throwable> =
    binding {
      val challengeAuth =
        database.awaitTransactionWithResult {
          database.socRecStartedChallengeAuthenticationQueries.getByRelationshipId(
            relationshipId = recoveryRelationshipId
          ).executeAsOneOrNull()
        }.bind()

      if (challengeAuth == null) {
        return@binding Ok(null).bind()
      }

      val privateKey =
        appPrivateKeyDao.getAsymmetricPrivateKey(challengeAuth.protectedCustomerRecoveryPakeKey.publicKey)
          .toErrorIfNull {
            SocRecKeyError.NoPrivateKeyAvailable(
              message = "SocRec challenge authentication private key missing"
            )
          }.bind()

      challengeAuth.copy(
        protectedCustomerRecoveryPakeKey = ProtectedCustomerRecoveryPakeKey(
          AppKeyImpl(
            CurveType.Curve25519,
            challengeAuth.protectedCustomerRecoveryPakeKey.publicKey,
            privateKey
          )
        )
      )
    }

  override suspend fun deleteByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<Unit, DbTransactionError> =
    database.awaitTransaction {
      database.socRecStartedChallengeAuthenticationQueries.deleteByRelationshipId(
        relationshipId = recoveryRelationshipId
      )
    }

  override suspend fun getAll(): Result<List<SocRecStartedChallengeAuthentication>, Throwable> =
    binding {
      val challengeAuths = database.awaitTransactionWithResult {
        database.socRecStartedChallengeAuthenticationQueries
          .getAll()
          .executeAsList()
      }.bind()

      challengeAuths.map {
        val privateKey =
          appPrivateKeyDao.getAsymmetricPrivateKey(it.protectedCustomerRecoveryPakeKey.publicKey)
            .toErrorIfNull {
              SocRecKeyError.NoPrivateKeyAvailable(
                message = "SocRec challenge authentication private key missing"
              )
            }.bind()

        it.copy(
          protectedCustomerRecoveryPakeKey = ProtectedCustomerRecoveryPakeKey(
            AppKeyImpl(
              CurveType.Curve25519,
              it.protectedCustomerRecoveryPakeKey.publicKey,
              privateKey
            )
          )
        )
      }
    }

  override suspend fun clear(): Result<Unit, DbTransactionError> =
    database.awaitTransaction {
      database.socRecStartedChallengeAuthenticationQueries.clear()
    }
}
