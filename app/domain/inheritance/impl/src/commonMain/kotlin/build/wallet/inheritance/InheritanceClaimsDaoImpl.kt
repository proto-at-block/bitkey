package build.wallet.inheritance

import app.cash.sqldelight.coroutines.asFlow
import build.wallet.bitkey.inheritance.BenefactorClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.bitkey.inheritance.InheritanceClaimKeyset
import build.wallet.bitkey.inheritance.InheritanceClaims
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.BitkeyDatabase
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class InheritanceClaimsDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : InheritanceClaimsDao {
  override val pendingBeneficiaryClaims: Flow<Result<List<BeneficiaryClaim.PendingClaim>, DbError>> =
    flow {
      val database = databaseProvider.database()
      database.inheritanceClaimsQueries
        .getPendingBeneficiaryClaims()
        .asFlow()
        .map { query ->
          database.awaitTransactionWithResult {
            query.executeAsList().map { row ->
              BeneficiaryClaim.PendingClaim(
                claimId = row.claimId,
                relationshipId = row.relationshipId,
                delayEndTime = row.delayEndTime,
                delayStartTime = row.delayStartTime,
                authKeys = InheritanceClaimKeyset(
                  appPubkey = row.appPubkey,
                  hardwarePubkey = row.hardwarePubkey
                )
              )
            }
          }
        }
        .distinctUntilChanged()
        .collect(::emit)
    }

  override val pendingBenefactorClaims: Flow<Result<List<BenefactorClaim.PendingClaim>, DbError>> =
    flow {
      val database = databaseProvider.database()
      database.inheritanceClaimsQueries
        .getPendingBenefactorClaims()
        .asFlow()
        .map { query ->
          database.awaitTransactionWithResult {
            query.executeAsList().map { row ->
              BenefactorClaim.PendingClaim(
                claimId = row.claimId,
                relationshipId = row.relationshipId,
                delayEndTime = row.delayEndTime,
                delayStartTime = row.delayStartTime
              )
            }
          }
        }
        .distinctUntilChanged()
        .collect(::emit)
    }

  override suspend fun setInheritanceClaims(
    inheritanceClaims: InheritanceClaims,
  ): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransactionWithResult {
      // Delete any existing claims
      inheritanceClaimsQueries.clearInheritanceClaims()

      val allClaims = inheritanceClaims.beneficiaryClaims + inheritanceClaims.benefactorClaims
      allClaims.forEach { claim ->
        insertClaim(claim)
      }
    }
  }

  override suspend fun updateInheritanceClaim(
    inheritanceClaim: InheritanceClaim,
  ): Result<Unit, Error> {
    return databaseProvider.database().awaitTransactionWithResult {
      inheritanceClaimsQueries.deleteClaimById(inheritanceClaim.claimId)
      insertClaim(inheritanceClaim)
    }
  }

  /**
   * Inserts a claim if it is of a type that is saved locally.
   */
  private fun BitkeyDatabase.insertClaim(inheritanceClaim: InheritanceClaim) {
    when (inheritanceClaim) {
      is BeneficiaryClaim.PendingClaim -> inheritanceClaimsQueries.insertPendingBeneficiaryClaim(
        claimId = inheritanceClaim.claimId,
        relationshipId = inheritanceClaim.relationshipId,
        delayEndTime = inheritanceClaim.delayEndTime,
        delayStartTime = inheritanceClaim.delayStartTime,
        appPubkey = inheritanceClaim.authKeys.appPubkey,
        hardwarePubkey = inheritanceClaim.authKeys.hardwarePubkey
      )
      is BenefactorClaim.PendingClaim -> inheritanceClaimsQueries.insertPendingBenefactorClaim(
        claimId = inheritanceClaim.claimId,
        relationshipId = inheritanceClaim.relationshipId,
        delayEndTime = inheritanceClaim.delayEndTime,
        delayStartTime = inheritanceClaim.delayStartTime
      )
      else -> {} // Only save necessary claims
    }
  }

  override suspend fun clear() =
    databaseProvider.database()
      .inheritanceClaimsQueries
      .awaitTransaction {
        clearInheritanceClaims()
      }
      .logFailure { "Failed to clear inheritance data from db." }
}
