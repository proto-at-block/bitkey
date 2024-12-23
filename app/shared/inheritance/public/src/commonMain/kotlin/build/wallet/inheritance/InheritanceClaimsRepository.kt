package build.wallet.inheritance

import build.wallet.bitkey.inheritance.BenefactorClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaims
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Coordinates the retrieval and storage of inheritance claims.
 *
 * This class will eagerly load claim data that is stored locally. Only
 * while observed will it fetch the latest claim data from the server.
 *
 * State is shared between observers of the collection.
 *
 * Only some of the claim data is stored offline. This is because some states
 * of claims, such as locked, are better refreshed with current server data
 * on each observation of the collection.
 */
interface InheritanceClaimsRepository {
  /**
   * Flow of the latest available data about user claims.
   *
   * Data from this flow may be stale (only locally saved data) and will
   * continue to emit updates while observed.
   *
   * This flow will not emit until a value is available, either by local
   * or server updates, and does not emit a default empty value initially
   * before load.
   */
  val claims: Flow<Result<InheritanceClaims, Error>>

  /**
   * Fetch the latest inheritance claims from the server immediately.
   *
   * This should be used sparingly! Prefer to rely on the claims flow
   * for the latest local information. This will only return if the network
   * request is successful, and will not return local results.
   */
  suspend fun fetchClaims(): Result<InheritanceClaims, Error>

  /**
   * Update the state of a single inheritance claim.
   */
  suspend fun updateSingleClaim(claim: BeneficiaryClaim)
}

/**
 * Flow that emits just the beneficiary claims.
 */
val InheritanceClaimsRepository.beneficiaryClaims: Flow<Result<List<BeneficiaryClaim>, Error>>
  get() = claims.map { it.map { it.beneficiaryClaims } }

/**
 * Flow that emits just the benefactor claims.
 */
val InheritanceClaimsRepository.benefactorClaims: Flow<Result<List<BenefactorClaim>, Error>>
  get() = claims.map { it.map { it.benefactorClaims } }

/**
 * Flow that emits just the beneficiary claims that are currently pending.
 */
val InheritanceClaimsRepository.pendingBeneficiaryClaims: Flow<Result<List<BeneficiaryClaim.PendingClaim>, Error>>
  get() = beneficiaryClaims.map {
    it.map { it.filterIsInstance<BeneficiaryClaim.PendingClaim>() }
  }

/**
 * Flow that emits just the benefactor claims that are currently pending.
 */
val InheritanceClaimsRepository.pendingBenefactorClaims: Flow<Result<List<BenefactorClaim.PendingClaim>, Error>>
  get() = benefactorClaims.map {
    it.map { it.filterIsInstance<BenefactorClaim.PendingClaim>() }
  }

/**
 * Flow that emits just the beneficiary claims that are currently locked.
 */
val InheritanceClaimsRepository.lockedBeneficiaryClaims: Flow<Result<List<BeneficiaryClaim.LockedClaim>, Error>>
  get() = beneficiaryClaims.map {
    it.map { it.filterIsInstance<BeneficiaryClaim.LockedClaim>() }
  }

/**
 * Flow that emits just the benefactor claims that are currently locked.
 */
val InheritanceClaimsRepository.lockedBenefactorClaims: Flow<Result<List<BenefactorClaim.LockedClaim>, Error>>
  get() = benefactorClaims.map {
    it.map { it.filterIsInstance<BenefactorClaim.LockedClaim>() }
  }
