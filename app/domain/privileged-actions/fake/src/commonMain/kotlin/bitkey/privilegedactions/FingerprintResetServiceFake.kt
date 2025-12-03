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
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

class FingerprintResetServiceFake(
  override val privilegedActionF8eClient: FingerprintResetF8eClient,
  override val accountService: AccountService = AccountServiceFake(),
  override val clock: Clock,
) : FingerprintResetService {
  val markGrantAsDeliveredCalls = mutableListOf<Unit>()
  val clearEnrolledFingerprintsCalls = mutableListOf<Unit>()
  val deleteFingerprintResetGrantCalls = mutableListOf<Unit>()
  private val _fingerprintResetAction = MutableStateFlow<PrivilegedActionInstance?>(null)
  private val _pendingGrant = MutableStateFlow<Grant?>(null)

  var completeFingerprintResetAndGetGrantResult: Result<Grant, PrivilegedActionError> = Ok(
    Grant(
      version = 1,
      serializedRequest = byteArrayOf(1, 2, 3, 4),
      appSignature = byteArrayOf(5, 6, 7, 8),
      wsmSignature = byteArrayOf(9, 10, 11, 12)
    )
  )
  var getLatestFingerprintResetActionResult: Result<PrivilegedActionInstance?, PrivilegedActionError> =
    Ok(null)

  var isGrantDelivered: Boolean = false
  var markGrantAsDeliveredResult: Result<Unit, DbError> = Ok(Unit)
  var clearEnrolledFingerprintsResult: Result<Unit, Error> = Ok(Unit)
  var deleteFingerprintResetGrantResult: Result<Unit, DbError> = Ok(Unit)

  override val fingerprintResetAction = _fingerprintResetAction

  override suspend fun createFingerprintResetPrivilegedAction(
    grantRequest: GrantRequest,
  ): Result<PrivilegedActionInstance, PrivilegedActionError> {
    val result = Ok(createDefaultPrivilegedActionInstance())
    result.onSuccess { _fingerprintResetAction.value = it }
    return result
  }

  override suspend fun completeFingerprintResetAndGetGrant(
    actionId: String,
    completionToken: String,
  ): Result<Grant, PrivilegedActionError> {
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
    _fingerprintResetAction.value = null
    _pendingGrant.value = null
    return Ok(Unit)
  }

  override suspend fun getLatestFingerprintResetAction(): Result<PrivilegedActionInstance?, PrivilegedActionError> {
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

    completeFingerprintResetAndGetGrantResult = Ok(
      Grant(
        version = 1,
        serializedRequest = byteArrayOf(1, 2, 3, 4),
        appSignature = byteArrayOf(5, 6, 7, 8),
        wsmSignature = byteArrayOf(9, 10, 11, 12)
      )
    )

    isGrantDelivered = false
    markGrantAsDeliveredResult = Ok(Unit)
    clearEnrolledFingerprintsResult = Ok(Unit)
    deleteFingerprintResetGrantResult = Ok(Unit)

    markGrantAsDeliveredCalls.clear()
    clearEnrolledFingerprintsCalls.clear()
    deleteFingerprintResetGrantCalls.clear()
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
    deleteFingerprintResetGrantCalls.add(Unit)
    _pendingGrant.value = null
    return deleteFingerprintResetGrantResult
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

  override suspend fun isGrantDelivered(): Boolean {
    return isGrantDelivered
  }

  override suspend fun markGrantAsDelivered(): Result<Unit, DbError> {
    isGrantDelivered = true
    markGrantAsDeliveredCalls.add(Unit)
    return markGrantAsDeliveredResult
  }

  override suspend fun clearEnrolledFingerprints(): Result<Unit, Error> {
    clearEnrolledFingerprintsCalls.add(Unit)
    return clearEnrolledFingerprintsResult
  }
}
