package build.wallet.recovery.socrec

import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentKey
import build.wallet.database.sqldelight.SocRecEnrollmentAuthentication
import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Result

/**
 * Data access object for [SocRecTrustedContactAuthentication], which stores the PAKE keys used
 * during enrollment.
 */
interface SocRecEnrollmentAuthenticationDao {
  suspend fun insert(
    recoveryRelationshipId: String,
    protectedCustomerEnrollmentKey: ProtectedCustomerEnrollmentKey,
    pakeCode: String,
  ): Result<Unit, Throwable>

  suspend fun getByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<SocRecEnrollmentAuthentication?, Throwable>

  suspend fun deleteByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<Unit, DbTransactionError>

  suspend fun clear(): Result<Unit, DbTransactionError>
}
