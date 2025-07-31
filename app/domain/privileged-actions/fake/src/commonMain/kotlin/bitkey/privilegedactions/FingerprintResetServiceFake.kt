package bitkey.privilegedactions

import bitkey.f8e.fingerprintreset.FingerprintResetF8eClient
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionType
import build.wallet.account.AccountService
import build.wallet.account.AccountServiceFake
import build.wallet.db.DbError
import build.wallet.grants.Grant
import build.wallet.grants.GrantRequest
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

class FingerprintResetServiceFake(
  override val privilegedActionF8eClient: FingerprintResetF8eClient = FingerprintResetF8eClientFake(
    { throw IllegalStateException("Turbine factory not provided") },
    clock = ClockFake()
  ),
  override val accountService: AccountService = AccountServiceFake(),
  override val clock: Clock,
) : FingerprintResetService {
  private val _fingerprintResetAction = MutableStateFlow<PrivilegedActionInstance?>(null)
  private val _pendingGrant = MutableStateFlow<Grant?>(null)

  var createFingerprintResetPrivilegedActionResult: Result<PrivilegedActionInstance, PrivilegedActionError> =
    Ok(
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
  var getLatestFingerprintResetActionResult: Result<PrivilegedActionInstance?, PrivilegedActionError> =
    Ok(null)

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
      result.onSuccess { grant ->
        _fingerprintResetAction.value = null
        _pendingGrant.value = grant
      }
    }
  }

  override suspend fun cancelFingerprintReset(
    cancellationToken: String,
  ): Result<Unit, PrivilegedActionError> {
    cancelFingerprintResetCalls.add(cancellationToken)
    return cancelFingerprintResetResult.also { result ->
      result.onSuccess {
        _fingerprintResetAction.value = null
        _pendingGrant.value = null
      }
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
  fun reset() {
    _fingerprintResetAction.value = null
    _pendingGrant.value = null
    getLatestFingerprintResetActionResult = Ok(null)

    createFingerprintResetPrivilegedActionResult = Ok(createDefaultPrivilegedActionInstance())
    completeFingerprintResetAndGetGrantResult = Ok(
      Grant(
        version = 1,
        serializedRequest = byteArrayOf(1, 2, 3, 4),
        signature = byteArrayOf(5, 6, 7, 8)
      )
    )
    cancelFingerprintResetResult = Ok(Unit)

    createFingerprintResetPrivilegedActionCalls.clear()
    completeFingerprintResetAndGetGrantCalls.clear()
    cancelFingerprintResetCalls.clear()
    getLatestFingerprintResetActionCalls.clear()
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

  override suspend fun getPendingFingerprintResetGrant(): Result<Grant?, DbError> {
    return Ok(_pendingGrant.value)
  }

  override suspend fun deleteFingerprintResetGrant(): Result<Unit, DbError> {
    _pendingGrant.value = null
    return Ok(Unit)
  }

  override fun pendingFingerprintResetGrant(): Flow<Grant?> = _pendingGrant

  override suspend fun getFingerprintResetState(): Result<FingerprintResetState, PrivilegedActionError> {
    return getLatestFingerprintResetAction()
      .map { pendingAction ->
        when (val authStrategy = pendingAction?.authorizationStrategy) {
          is AuthorizationStrategy.DelayAndNotify -> {
            if (pendingAction.isDelayAndNotifyReadyToComplete(clock)) {
              FingerprintResetState.DelayCompleted(pendingAction)
            } else {
              FingerprintResetState.DelayInProgress(pendingAction, authStrategy)
            }
          }
          else -> {
            val grant = _pendingGrant.value
            if (grant != null) {
              FingerprintResetState.GrantReady(grant)
            } else {
              FingerprintResetState.None
            }
          }
        }
      }
  }
}
