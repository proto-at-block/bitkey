package build.wallet.inheritance

import build.wallet.bitkey.inheritance.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock

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
  suspend fun updateSingleClaim(claim: InheritanceClaim)

  /**
   * Sync the latest server data with cache and database.
   *
   * This will only update the database if the server data is successful,
   * and it will only update the in-memory state if there is no existing
   * results.
   */
  suspend fun syncServerClaims()
}

/**
 * Flow that will emit the current collection of claims, paired with a timestamp
 * that the data was collected.
 *
 * When a pending claim crosses its complete time, this will emit a new
 * collection with an updated timestamp. This allows the application UI to
 * update without constantly scanning the clock or polling to refresh.
 */
fun InheritanceClaimsRepository.getClaimsSnapshot(clock: Clock): Flow<ClaimsSnapshot> {
  return claims.flatMapLatest { result ->
    val latestClaims = result.get() ?: InheritanceClaims.EMPTY
    flow {
      while (currentCoroutineContext().isActive) {
        val now = clock.now()
        emit(ClaimsSnapshot(now, latestClaims))
        val firstExpiring = latestClaims.all
          .filterNot { it.isApproved(now) }
          .mapNotNull {
            when (it) {
              is BeneficiaryClaim.PendingClaim -> it.delayEndTime
              is BenefactorClaim.PendingClaim -> it.delayEndTime
              else -> null
            }
          }
          .minOfOrNull { it }
          ?: break

        delay(firstExpiring - now)
      }
    }
  }
}
