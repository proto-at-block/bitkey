package build.wallet.recovery.socrec

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.PakeCode
import build.wallet.bitkey.relationships.ProtectedCustomerEnrollmentPakeKey
import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Result

/**
 * Data access object for [SocRecTrustedContactAuthentication], which stores the PAKE keys used
 * during enrollment.
 */
interface SocRecEnrollmentAuthenticationDao {
  suspend fun insert(
    recoveryRelationshipId: String,
    protectedCustomerEnrollmentPakeKey: AppKey<ProtectedCustomerEnrollmentPakeKey>,
    pakeCode: PakeCode,
  ): Result<Unit, Throwable>

  suspend fun getByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<SocRecEnrollmentAuthenticationRow?, Throwable>

  suspend fun deleteByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<Unit, DbTransactionError>

  suspend fun clear(): Result<Unit, DbTransactionError>

  data class SocRecEnrollmentAuthenticationRow(
    val recoveryRelationshipId: String,
    val protectedCustomerEnrollmentPakeKey: AppKey<ProtectedCustomerEnrollmentPakeKey>,
    val pakeCode: PakeCode,
  )
}
