package build.wallet.auth

import build.wallet.bitkey.app.AppAuthPublicKeys
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

// TODO: Move this to the impl

sealed class AuthKeyRotationAttemptDaoState {
  data object NoAttemptInProgress : AuthKeyRotationAttemptDaoState()

  data object KeyRotationProposalWritten : AuthKeyRotationAttemptDaoState()

  data class AuthKeysWritten(
    val appAuthPublicKeys: AppAuthPublicKeys,
  ) : AuthKeyRotationAttemptDaoState()
}

interface AuthKeyRotationAttemptDao {
  fun observeAuthKeyRotationAttemptState(): Flow<Result<AuthKeyRotationAttemptDaoState, Throwable>>

  suspend fun setKeyRotationProposal(): Result<Unit, Error>

  suspend fun setAuthKeysWritten(appAuthPublicKeys: AppAuthPublicKeys): Result<Unit, Error>

  suspend fun clear(): Result<Unit, Error>
}
