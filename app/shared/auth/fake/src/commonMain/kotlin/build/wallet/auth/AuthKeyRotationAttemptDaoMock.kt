package build.wallet.auth

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.app.AppAuthPublicKeys
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
  val setKeyRotationProposalCalls = turbine("setServerRotationAttemptComplete calls")
  val clearCalls = turbine("clear calls AuthKeyRotationAttemptDaoMock")
  val stateFlow = MutableSharedFlow<AuthKeyRotationAttemptDaoState>()

  override fun observeAuthKeyRotationAttemptState(): Flow<Result<AuthKeyRotationAttemptDaoState, Throwable>> {
    getAuthKeyRotationAttemptStateCalls += Unit
    return stateFlow.map { Ok(it) }
  }

  override suspend fun setKeyRotationProposal(): Result<Unit, DbError> {
    setKeyRotationProposalCalls += Unit
    return Ok(Unit)
  }

  override suspend fun setAuthKeysWritten(
    appAuthPublicKeys: AppAuthPublicKeys,
  ): Result<Unit, DbError> {
    setAuthKeysWrittenCalls += Unit
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, DbError> {
    clearCalls += Unit
    return Ok(Unit)
  }
}
