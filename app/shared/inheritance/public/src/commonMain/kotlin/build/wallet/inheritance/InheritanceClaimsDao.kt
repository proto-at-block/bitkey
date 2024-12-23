package build.wallet.inheritance

import build.wallet.bitkey.inheritance.BenefactorClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.bitkey.inheritance.InheritanceClaims
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Responsible for storing [InheritanceClaims].
 */
interface InheritanceClaimsDao {
  /**
   * Synced pending claims for a beneficiary.
   */
  val pendingBeneficiaryClaims: Flow<Result<List<BeneficiaryClaim.PendingClaim>, Error>>

  /**
   * Synced pending claims for a benefactor.
   */
  val pendingBenefactorClaims: Flow<Result<List<BenefactorClaim.PendingClaim>, Error>>

  /**
   * Sets (overwrites) all inheritances claims in the database.
   */
  suspend fun setInheritanceClaims(inheritanceClaims: InheritanceClaims): Result<Unit, Error>

  /**
   * Updates a single claim in the database.
   */
  suspend fun updateInheritanceClaim(inheritanceClaim: InheritanceClaim): Result<Unit, Error>

  /**
   * Clears all details for all claims from the dao
   */
  suspend fun clear(): Result<Unit, Error>
}
