package build.wallet.auth

import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

// TODO: Move this to the impl

sealed class AuthKeyRotationAttemptDaoState {
  data object NoAttemptInProgress : AuthKeyRotationAttemptDaoState()

  data class AuthKeysWritten(
    val appAuthPublicKeys: AppAuthPublicKeys,
    val hwAuthPublicKey: HwAuthPublicKey,
  ) : AuthKeyRotationAttemptDaoState()

  data class ServerRotationAttemptComplete(
    val appAuthPublicKeys: AppAuthPublicKeys,
  ) : AuthKeyRotationAttemptDaoState()
}

interface AuthKeyRotationAttemptDao {
  fun getAuthKeyRotationAttemptState(): Flow<Result<AuthKeyRotationAttemptDaoState, Throwable>>

  suspend fun setAuthKeysWritten(
    appAuthPublicKeys: AppAuthPublicKeys,
    hwAuthPublicKey: HwAuthPublicKey,
  ): Result<Unit, DbError>

  suspend fun setServerRotationAttemptComplete(): Result<Unit, DbError>

  suspend fun clear(): Result<Unit, DbError>
}
