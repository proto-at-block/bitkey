package build.wallet.recovery.socrec

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.PakeCode
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import com.github.michaelbull.result.Result

/**
 * Data access object for [SocRecStartedChallengeAuthentication], which persists challenge/pake keys
 * across app sessions.
 */
interface SocRecStartedChallengeAuthenticationDao {
  suspend fun insert(
    recoveryRelationshipId: String,
    protectedCustomerRecoveryPakeKey: AppKey<ProtectedCustomerRecoveryPakeKey>,
    pakeCode: PakeCode,
  ): Result<Unit, Throwable>

  suspend fun getByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<SocRecStartedChallengeAuthenticationRow?, Throwable>

  suspend fun deleteByRelationshipId(recoveryRelationshipId: String): Result<Unit, Error>

  suspend fun getAll(): Result<List<SocRecStartedChallengeAuthenticationRow>, Throwable>

  suspend fun clear(): Result<Unit, Error>

  data class SocRecStartedChallengeAuthenticationRow(
    val relationshipId: String,
    val protectedCustomerRecoveryPakeKey: AppKey<ProtectedCustomerRecoveryPakeKey>,
    val pakeCode: PakeCode,
  )
}
