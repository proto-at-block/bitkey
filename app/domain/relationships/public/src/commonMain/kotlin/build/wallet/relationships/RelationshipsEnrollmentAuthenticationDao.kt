package build.wallet.relationships

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.PakeCode
import build.wallet.bitkey.relationships.ProtectedCustomerEnrollmentPakeKey
import com.github.michaelbull.result.Result

/**
 * Data access object for [SocRecTrustedContactAuthentication], which stores the PAKE keys used
 * during enrollment.
 */
interface RelationshipsEnrollmentAuthenticationDao {
  suspend fun insert(
    recoveryRelationshipId: String,
    protectedCustomerEnrollmentPakeKey: AppKey<ProtectedCustomerEnrollmentPakeKey>,
    pakeCode: PakeCode,
  ): Result<Unit, Throwable>

  suspend fun getByRelationshipId(
    recoveryRelationshipId: String,
  ): Result<RelationshipsEnrollmentAuthenticationRow?, Throwable>

  suspend fun deleteByRelationshipId(recoveryRelationshipId: String): Result<Unit, Error>

  suspend fun clear(): Result<Unit, Error>

  data class RelationshipsEnrollmentAuthenticationRow(
    val relationshipId: String,
    val protectedCustomerEnrollmentPakeKey: AppKey<ProtectedCustomerEnrollmentPakeKey>,
    val pakeCode: PakeCode,
  )
}
