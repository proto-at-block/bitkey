package build.wallet.recovery.socrec

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.crypto.PrivateKey
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.SocRecStartedChallengeAuthentication
import build.wallet.db.DbTransactionError
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.toErrorIfNull

class SocRecStartedChallengeAuthenticationDaoImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  databaseProviderImpl: BitkeyDatabaseProvider,
) : SocRecStartedChallengeAuthenticationDao {
  private val database by lazy { databaseProviderImpl.database() }

  override suspend fun insert(
    recoveryRelationshipId: String,
    protectedCustomerRecoveryPakeKey: AppKey<ProtectedCustomerRecoveryPakeKey>,
    pakeCode: PakeCode,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      appPrivateKeyDao.storeAsymmetricPrivateKey(
        protectedCustomerRecoveryPakeKey.publicKey,
        requireNotNull(protectedCustomerRecoveryPakeKey.privateKey)
      ).bind()
      database.awaitTransactionWithResult {
        database.socRecStartedChallengeAuthenticationQueries.insert(
          relationshipId = recoveryRelationshipId,
          protectedCustomerRecoveryPakeKey = protectedCustomerRecoveryPakeKey.publicKey,
          pakeCode = pakeCode.bytes
        )
      }.bind()
    }

  override suspend fun getByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<SocRecStartedChallengeAuthenticationDao.SocRecStartedChallengeAuthenticationRow?, Throwable> =
    coroutineBinding<SocRecStartedChallengeAuthenticationDao.SocRecStartedChallengeAuthenticationRow?, Throwable> {
      val challengeAuth =
        database.awaitTransactionWithResult {
          database.socRecStartedChallengeAuthenticationQueries.getByRelationshipId(
            relationshipId = recoveryRelationshipId
          ).executeAsOneOrNull()
        }.bind()

      if (challengeAuth == null) {
        return@coroutineBinding Ok(null).bind()
      }

      val privateKey =
        appPrivateKeyDao.getAsymmetricPrivateKey(challengeAuth.protectedCustomerRecoveryPakeKey)
          .toErrorIfNull {
            SocRecKeyError.NoPrivateKeyAvailable(
              message = "SocRec challenge authentication private key missing"
            )
          }.bind()

      challengeAuth.toRow(privateKey)
    }

  override suspend fun deleteByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<Unit, DbTransactionError> =
    database.awaitTransaction {
      database.socRecStartedChallengeAuthenticationQueries.deleteByRelationshipId(
        relationshipId = recoveryRelationshipId
      )
    }

  override suspend fun getAll(): Result<List<SocRecStartedChallengeAuthenticationDao.SocRecStartedChallengeAuthenticationRow>, Throwable> =
    coroutineBinding {
      val challengeAuths = database.awaitTransactionWithResult {
        database.socRecStartedChallengeAuthenticationQueries
          .getAll()
          .executeAsList()
      }.bind()

      challengeAuths.map {
        val privateKey =
          appPrivateKeyDao.getAsymmetricPrivateKey(it.protectedCustomerRecoveryPakeKey)
            .toErrorIfNull {
              SocRecKeyError.NoPrivateKeyAvailable(
                message = "SocRec challenge authentication private key missing"
              )
            }.bind()

        it.toRow(privateKey)
      }
    }

  override suspend fun clear(): Result<Unit, DbTransactionError> =
    database.awaitTransaction {
      database.socRecStartedChallengeAuthenticationQueries.clear()
    }
}

private fun SocRecStartedChallengeAuthentication.toRow(
  privateKey: PrivateKey<ProtectedCustomerRecoveryPakeKey>,
): SocRecStartedChallengeAuthenticationDao.SocRecStartedChallengeAuthenticationRow =
  SocRecStartedChallengeAuthenticationDao.SocRecStartedChallengeAuthenticationRow(
    relationshipId = relationshipId,
    protectedCustomerRecoveryPakeKey = AppKey(
      protectedCustomerRecoveryPakeKey,
      privateKey
    ),
    pakeCode = PakeCode(pakeCode)
  )
