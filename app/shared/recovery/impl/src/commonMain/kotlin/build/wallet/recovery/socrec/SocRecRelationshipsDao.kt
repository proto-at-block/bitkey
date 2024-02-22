package build.wallet.recovery.socrec

import build.wallet.bitkey.socrec.TrustedContactAuthenticationState
import build.wallet.db.DbError
import build.wallet.f8e.socrec.SocRecRelationships
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface SocRecRelationshipsDao {
  fun socRecRelationships(): Flow<Result<SocRecRelationships, DbError>>

  suspend fun setSocRecRelationships(
    socRecRelationships: SocRecRelationships,
  ): Result<Unit, DbError>

  suspend fun setUnendorsedTrustedContactAuthenticationState(
    recoveryRelationshipId: String,
    authenticationState: TrustedContactAuthenticationState,
  ): Result<Unit, DbError>

  suspend fun clear(): Result<Unit, DbError>
}
