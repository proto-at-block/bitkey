package build.wallet.auth

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map

class AuthKeyRotationAttemptDaoMock(
  val turbine: (String) -> Turbine<Any>,
) : AuthKeyRotationAttemptDao {
  val getAuthKeyRotationAttemptStateCalls = turbine("getAuthKeyRotationAttemptState calls")
  val setAuthKeysWrittenCalls = turbine("setAuthKeysWritten calls")
  val setServerRotationAttemptCompleteCalls = turbine("setServerRotationAttemptComplete calls")
  val clearCalls = turbine("clear calls AuthKeyRotationAttemptDaoMock")
  val stateFlow = MutableSharedFlow<AuthKeyRotationAttemptDaoState>()

  override fun getAuthKeyRotationAttemptState(): Flow<Result<AuthKeyRotationAttemptDaoState, Throwable>> {
    getAuthKeyRotationAttemptStateCalls += Unit
    return stateFlow.map { Ok(it) }
  }

  override suspend fun setAuthKeysWritten(
    appAuthPublicKeys: AppAuthPublicKeys,
    hwAuthPublicKey: HwAuthPublicKey,
  ): Result<Unit, DbError> {
    setAuthKeysWrittenCalls += Unit
    return Ok(Unit)
  }

  override suspend fun setServerRotationAttemptComplete(): Result<Unit, DbError> {
    setServerRotationAttemptCompleteCalls += Unit
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, DbError> {
    clearCalls += Unit
    return Ok(Unit)
  }
}
