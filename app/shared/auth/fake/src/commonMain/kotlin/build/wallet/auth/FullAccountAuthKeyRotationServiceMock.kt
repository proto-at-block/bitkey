package build.wallet.auth

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.account.FullAccount
import com.github.michaelbull.result.Ok
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

typealias StartOrResumeAuthKeyRotationMock = suspend (
  request: AuthKeyRotationRequest,
  account: FullAccount,
) -> AuthKeyRotationResult

class FullAccountAuthKeyRotationServiceMock(
  turbine: (String) -> Turbine<Any>,
) : FullAccountAuthKeyRotationService {
  val rotateAuthKeysCalls = turbine("rotate auth keys calls FullAccountAuthKeyRotationServiceMock")
  val successAcknowledgedCalls = turbine("success acknowledged calls FullAccountAuthKeyRotationServiceMock")

  val pendingKeyRotationAttempt = MutableStateFlow<PendingAuthKeyRotationAttempt?>(null)
  val rotationResult = MutableStateFlow(
    defaultSuccess(successAcknowledgedCalls)
  )

  val recommendKeyRotationCalls = turbine("recommend key rotation calls FullAccountAuthKeyRotationServiceMock")

  override suspend fun startOrResumeAuthKeyRotation(
    request: AuthKeyRotationRequest,
    account: FullAccount,
  ): AuthKeyRotationResult {
    rotateAuthKeysCalls += Unit
    return rotationResult.value(request, account)
  }

  override fun observePendingKeyRotationAttemptUntilNull(): Flow<PendingAuthKeyRotationAttempt?> {
    return flow {
      emitAll(pendingKeyRotationAttempt)
    }
  }

  override suspend fun recommendKeyRotation() {
    recommendKeyRotationCalls += Unit
    pendingKeyRotationAttempt.value = PendingAuthKeyRotationAttempt.ProposedAttempt
  }

  override suspend fun dismissProposedRotationAttempt() {
    pendingKeyRotationAttempt.value = null
  }

  fun reset() {
    rotationResult.value = defaultSuccess(successAcknowledgedCalls)
    pendingKeyRotationAttempt.value = null
  }

  companion object {
    fun defaultSuccess(onAcknowledge: Turbine<Any>): StartOrResumeAuthKeyRotationMock {
      return { _, _ ->
        Ok(
          AuthKeyRotationSuccess(onAcknowledge = {
            onAcknowledge += Unit
          })
        )
      }
    }
  }
}
