package build.wallet.recovery.socrec

import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.database.sqldelight.SocRecStartedChallengeAuthentication
import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Result

/**
 * Data access object for [SocRecStartedChallengeAuthentication], which persists challenge/pake keys
 * across app sessions.
 */
interface SocRecStartedChallengeAuthenticationDao {
  suspend fun insert(
    recoveryRelationshipId: String,
    protectedCustomerRecoveryPakeKey: ProtectedCustomerRecoveryPakeKey,
    pakeCode: PakeCode,
  ): Result<Unit, Throwable>

  suspend fun getByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<SocRecStartedChallengeAuthentication?, Throwable>

  suspend fun deleteByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<Unit, DbTransactionError>

  suspend fun getAll(): Result<List<SocRecStartedChallengeAuthentication>, Throwable>

  suspend fun clear(): Result<Unit, DbTransactionError>
}
