package build.wallet.relationships

import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import build.wallet.db.DbError
import build.wallet.f8e.relationships.Relationships
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface RelationshipsDao {
  /**
   * An unfiltered flow of all [Relationships] in the database.
   */
  fun relationships(): Flow<Result<Relationships, DbError>>

  suspend fun setRelationships(relationships: Relationships): Result<Unit, DbError>

  suspend fun setUnendorsedTrustedContactAuthenticationState(
    recoveryRelationshipId: String,
    authenticationState: TrustedContactAuthenticationState,
  ): Result<Unit, DbError>

  suspend fun setTrustedContactAuthenticationState(
    recoveryRelationshipId: String,
    authenticationState: TrustedContactAuthenticationState,
  ): Result<Unit, DbError>

  suspend fun clear(): Result<Unit, DbError>
}
