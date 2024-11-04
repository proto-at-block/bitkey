package build.wallet.inheritance

import app.cash.sqldelight.coroutines.asFlow
import build.wallet.bitkey.inheritance.BenefactorClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaimKeyset
import build.wallet.bitkey.inheritance.InheritanceClaims
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class InheritanceClaimsDaoImpl(
  databaseProvider: BitkeyDatabaseProvider,
) : InheritanceClaimsDao {
  private val database by lazy {
    databaseProvider.database()
  }

  override val pendingBeneficiaryClaims: Flow<Result<List<BeneficiaryClaim.PendingClaim>, DbError>> = database.inheritanceClaimsQueries
    .getPendingBeneficiaryClaims()
    .asFlow()
    .map { query ->
      database.awaitTransactionWithResult {
        query.executeAsList().map {
          BeneficiaryClaim.PendingClaim(
            claimId = it.claimId,
            relationshipId = it.relationshipId,
            delayEndTime = it.delayEndTime,
            delayStartTime = it.delayStartTime,
            authKeys = InheritanceClaimKeyset(
              appPubkey = it.appPubkey,
              hardwarePubkey = it.hardwarePubkey
            )
          )
        }
      }
    }
    .distinctUntilChanged()

  override val pendingBenefactorClaims: Flow<Result<List<BenefactorClaim.PendingClaim>, DbError>> =
    database.inheritanceClaimsQueries
      .getPendingBenefactorClaims()
      .asFlow()
      .map { query ->
        database.awaitTransactionWithResult {
          query.executeAsList().map {
            BenefactorClaim.PendingClaim(
              claimId = it.claimId,
              relationshipId = it.relationshipId,
              delayEndTime = it.delayEndTime,
              delayStartTime = it.delayStartTime
            )
          }
        }
      }
      .distinctUntilChanged()

  override suspend fun setInheritanceClaims(
    inheritanceClaims: InheritanceClaims,
  ): Result<Unit, DbError> {
    return database.awaitTransactionWithResult {
      // Delete any existing claims
      inheritanceClaimsQueries.clearInheritanceClaims()

      inheritanceClaims.beneficiaryClaims.forEach {
        when (it) {
          is BeneficiaryClaim.PendingClaim -> inheritanceClaimsQueries.insertPendingBeneficiaryClaim(
            claimId = it.claimId,
            relationshipId = it.relationshipId,
            delayEndTime = it.delayEndTime,
            delayStartTime = it.delayStartTime,
            appPubkey = it.authKeys.appPubkey,
            hardwarePubkey = it.authKeys.hardwarePubkey
          )
          else -> Unit // Only save necessary claims
        }
      }

      inheritanceClaims.benefactorClaims.forEach {
        when (it) {
          is BenefactorClaim.PendingClaim -> inheritanceClaimsQueries.insertPendingBenefactorClaim(
            claimId = it.claimId,
            relationshipId = it.relationshipId,
            delayEndTime = it.delayEndTime,
            delayStartTime = it.delayStartTime
          )
          else -> Unit // Only save necessary claims
        }
      }
    }
  }

  override suspend fun clear() =
    database.inheritanceClaimsQueries
      .awaitTransaction {
        clearInheritanceClaims()
      }
      .logFailure { "Failed to clear inheritance data from db." }
}
