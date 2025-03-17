package build.wallet.relationships

import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface RelationshipsDao {
  /**
   * An unfiltered flow of all [Relationships] in the database.
   */
  fun relationships(): Flow<Result<Relationships, Error>>

  suspend fun setRelationships(relationships: Relationships): Result<Unit, Error>

  suspend fun setUnendorsedTrustedContactAuthenticationState(
    recoveryRelationshipId: String,
    authenticationState: TrustedContactAuthenticationState,
  ): Result<Unit, Error>

  suspend fun setTrustedContactAuthenticationState(
    recoveryRelationshipId: String,
    authenticationState: TrustedContactAuthenticationState,
  ): Result<Unit, Error>

  suspend fun clear(): Result<Unit, Error>
}
