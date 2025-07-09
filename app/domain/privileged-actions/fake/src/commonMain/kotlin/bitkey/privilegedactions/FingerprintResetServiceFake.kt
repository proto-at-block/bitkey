package bitkey.privilegedactions

import bitkey.f8e.fingerprintreset.FingerprintResetF8eClient
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionType
import build.wallet.account.AccountService
import build.wallet.account.AccountServiceFake
import build.wallet.grants.Grant
import build.wallet.grants.GrantRequest
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

class FingerprintResetServiceFake(
  override val privilegedActionF8eClient: FingerprintResetF8eClient = FingerprintResetF8eClientFake(
    { throw IllegalStateException("Turbine factory not provided") },
    Clock.System
  ),
  override val accountService: AccountService = AccountServiceFake(),
  override val clock: Clock,
) : FingerprintResetService {
  private val _fingerprintResetAction = MutableStateFlow<PrivilegedActionInstance?>(null)

  var createFingerprintResetPrivilegedActionResult: Result<PrivilegedActionInstance, PrivilegedActionError> = Ok(
    createDefaultPrivilegedActionInstance()
  )
  var completeFingerprintResetAndGetGrantResult: Result<Grant, PrivilegedActionError> = Ok(
    Grant(
      version = 1,
      serializedRequest = byteArrayOf(1, 2, 3, 4),
      signature = byteArrayOf(5, 6, 7, 8)
    )
  )
  var cancelFingerprintResetResult: Result<Unit, PrivilegedActionError> = Ok(Unit)
  var getLatestFingerprintResetActionResult: Result<PrivilegedActionInstance?, PrivilegedActionError> = Ok(null)

  val createFingerprintResetPrivilegedActionCalls = mutableListOf<GrantRequest>()
  val completeFingerprintResetAndGetGrantCalls = mutableListOf<Pair<String, String>>()
  val cancelFingerprintResetCalls = mutableListOf<String>()
  val getLatestFingerprintResetActionCalls = mutableListOf<Unit>()

  override fun fingerprintResetAction(): StateFlow<PrivilegedActionInstance?> =
    _fingerprintResetAction

  override suspend fun createFingerprintResetPrivilegedAction(
    grantRequest: GrantRequest,
  ): Result<PrivilegedActionInstance, PrivilegedActionError> {
    createFingerprintResetPrivilegedActionCalls.add(grantRequest)
    return createFingerprintResetPrivilegedActionResult.also { result ->
      result.onSuccess { _fingerprintResetAction.value = it }
    }
  }

  override suspend fun completeFingerprintResetAndGetGrant(
    actionId: String,
    completionToken: String,
  ): Result<Grant, PrivilegedActionError> {
    completeFingerprintResetAndGetGrantCalls.add(actionId to completionToken)
    return completeFingerprintResetAndGetGrantResult.also { result ->
      result.onSuccess { _fingerprintResetAction.value = null }
    }
  }

  override suspend fun cancelFingerprintReset(
    cancellationToken: String,
  ): Result<Unit, PrivilegedActionError> {
    cancelFingerprintResetCalls.add(cancellationToken)
    return cancelFingerprintResetResult.also { result ->
      result.onSuccess { _fingerprintResetAction.value = null }
    }
  }

  override suspend fun getLatestFingerprintResetAction(): Result<PrivilegedActionInstance?, PrivilegedActionError> {
    getLatestFingerprintResetActionCalls.add(Unit)
    return getLatestFingerprintResetActionResult.also { result ->
      result.onSuccess { _fingerprintResetAction.value = it }
    }
  }

  /**
   * Helper method to set up a pending fingerprint reset action
   */
  fun setupPendingFingerprintReset(delayEndTime: Instant = clock.now().plus(6.days)) {
    val pendingAction = PrivilegedActionInstance(
      id = "fake-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now().minus(1.days),
        delayEndTime = delayEndTime,
        cancellationToken = "fake-cancellation-token",
        completionToken = "fake-completion-token"
      )
    )
    getLatestFingerprintResetActionResult = Ok(pendingAction)
    _fingerprintResetAction.value = pendingAction
  }

  /**
   * Helper method to set up a fingerprint reset action that's ready to be completed
   */
  fun setupReadyToCompleteFingerprintReset() {
    setupPendingFingerprintReset(clock.now().minus(1.days))
  }

  /**
   * Helper method to clear any pending fingerprint reset actions
   */
  fun clearPendingFingerprintReset() {
    getLatestFingerprintResetActionResult = Ok(null)
    _fingerprintResetAction.value = null
  }

  private fun createDefaultPrivilegedActionInstance(): PrivilegedActionInstance {
    return PrivilegedActionInstance(
      id = "fake-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now().minus(1.days),
        delayEndTime = clock.now().plus(6.days),
        cancellationToken = "fake-cancellation-token",
        completionToken = "fake-completion-token"
      )
    )
  }
}
