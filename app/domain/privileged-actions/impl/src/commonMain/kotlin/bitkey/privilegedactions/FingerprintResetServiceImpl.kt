package bitkey.privilegedactions

import bitkey.f8e.fingerprintreset.FingerprintResetF8eClient
import bitkey.f8e.fingerprintreset.FingerprintResetRequest
import bitkey.f8e.privilegedactions.Authorization
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.CancelPrivilegedActionRequest
import bitkey.f8e.privilegedactions.ContinuePrivilegedActionRequest
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionInstanceRef
import bitkey.f8e.privilegedactions.PrivilegedActionType
import bitkey.firmware.HardwareUnlockInfoService
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SignatureUtils
import build.wallet.grants.GRANT_SIGNATURE_LEN
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.logging.logError
import build.wallet.logging.logInfo
import build.wallet.worker.RetryStrategy
import build.wallet.worker.RunStrategy
import build.wallet.worker.TimeoutStrategy
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class FingerprintResetServiceImpl(
  override val privilegedActionF8eClient: FingerprintResetF8eClient,
  override val accountService: AccountService,
  override val clock: Clock,
  private val signatureUtils: SignatureUtils,
  private val grantDao: GrantDao,
  private val hardwareUnlockInfoService: HardwareUnlockInfoService,
) : FingerprintResetService, FingerprintResetSyncWorker {
  private val fingerprintResetActionCache = MutableStateFlow<PrivilegedActionInstance?>(null)

  override val runStrategy: Set<RunStrategy> = setOf(RunStrategy.Startup())
  override val timeout: TimeoutStrategy = TimeoutStrategy.Always(30.seconds)
  override val retryStrategy: RetryStrategy = RetryStrategy.Always(retries = 1, delay = 1.seconds)

  override suspend fun executeWork() {
    // Sync the latest fingerprint reset status on app launch to avoid UI flashing. Best effort.
    getLatestFingerprintResetAction()
  }

  override val fingerprintResetAction = fingerprintResetActionCache

  /**
   * Create a fingerprint reset privileged action using a GrantRequest
   */
  override suspend fun createFingerprintResetPrivilegedAction(
    grantRequest: GrantRequest,
  ): Result<PrivilegedActionInstance, PrivilegedActionError> {
    if (grantRequest.action != GrantAction.FINGERPRINT_RESET) {
      return Err(PrivilegedActionError.UnsupportedActionType)
    }

    return accountService.getAccount<FullAccount>()
      .mapError { accountError ->
        PrivilegedActionError.IncorrectAccountType
      }
      .flatMap { account ->
        val hwAuthPublicKey = account.keybox.activeHwKeyBundle.authKey

        val derEncodedSignature = try {
          derEncodedSignature(grantRequest.signature)
        } catch (e: IllegalArgumentException) {
          return@flatMap Err(PrivilegedActionError.InvalidRequest(e))
        }

        val request = FingerprintResetRequest(
          version = grantRequest.version.toInt(),
          action = grantRequest.action.value,
          deviceId = grantRequest.deviceId.toByteString().base64(),
          challenge = grantRequest.challenge.toByteString().base64(),
          signature = derEncodedSignature.hex(),
          hwAuthPublicKey = hwAuthPublicKey.pubKey.value
        )
        createAction(request)
      }
      .also { result ->
        result.onSuccess { instance -> fingerprintResetActionCache.value = instance }
      }
  }

  override suspend fun completeFingerprintResetAndGetGrant(
    actionId: String,
    completionToken: String,
  ): Result<Grant, PrivilegedActionError> =
    coroutineBinding {
      val account = accountService.getAccount<FullAccount>()
        .mapError { PrivilegedActionError.IncorrectAccountType }
        .bind()

      val instances = privilegedActionF8eClient.getPrivilegedActionInstances(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId
      ).mapError { throwable: Throwable -> PrivilegedActionError.ServerError(throwable) }
        .bind()

      val actionInstance = instances.find { it.id == actionId }
        ?: Err(PrivilegedActionError.NotAuthorized).bind()

      val auth = Authorization(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        completionToken = completionToken
      )

      val actionRef = PrivilegedActionInstanceRef(
        id = actionInstance.id,
        authorizationStrategy = auth
      )

      val request = ContinuePrivilegedActionRequest(
        privilegedActionInstance = actionRef
      )

      val fingerprintResetResponse = privilegedActionF8eClient.continuePrivilegedAction(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId,
        request = request
      ).mapError { throwable: Throwable -> PrivilegedActionError.ServerError(throwable) }
        .bind()

      // Clear the cached action after completing the fingerprint reset - we can only fetch the
      // grant once, so as soon as that completes successfully, we reset the cache.
      fingerprintResetActionCache.value = null

      val grant = try {
        val serializedRequest = fingerprintResetResponse.serializedRequest.decodeBase64()
          ?: Err(
            PrivilegedActionError.InvalidResponse(
              IllegalArgumentException("Invalid base64 serializedRequest")
            )
          ).bind()

        val derEncodedSignature = fingerprintResetResponse.signature.decodeHex()
        val compactSignature = signatureUtils.decodeSignatureFromDer(derEncodedSignature)

        Grant(
          version = fingerprintResetResponse.version.toByte(),
          serializedRequest = serializedRequest.toByteArray(),
          signature = compactSignature
        )
      } catch (e: IllegalArgumentException) {
        Err(PrivilegedActionError.InvalidResponse(e)).bind()
      }

      // Persist the grant to the database
      grantDao.saveGrant(
        grant = grant
      ).onFailure { dbError ->
        // Log the error but don't fail the operation
        // The grant can still be used even if persistence fails
        logError { "Failed to persist grant: actionId=$actionId, error=$dbError" }
      }

      grant
    }

  override suspend fun cancelFingerprintReset(
    cancellationToken: String,
  ): Result<Unit, PrivilegedActionError> =
    coroutineBinding {
      // Check if there's a persisted grant in the database first
      val persistedGrant = getPendingFingerprintResetGrant().get()

      if (persistedGrant != null) {
        // If we have a grant in the database, just delete it locally
        // (the server-side action was already consumed when we retrieved the grant)
        logInfo { "Cancelling fingerprint reset by deleting persisted grant from database" }
        grantDao.deleteGrantByAction(GrantAction.FINGERPRINT_RESET)
          .mapError { dbError ->
            PrivilegedActionError.DatabaseError(dbError)
          }
          .bind()

        fingerprintResetActionCache.value = null
      } else {
        // No persisted grant, proceed with server cancellation
        val account = accountService.getAccount<FullAccount>()
          .mapError { PrivilegedActionError.IncorrectAccountType }
          .bind()

        privilegedActionF8eClient.cancelFingerprintReset(
          f8eEnvironment = account.config.f8eEnvironment,
          fullAccountId = account.accountId,
          request = CancelPrivilegedActionRequest(cancellationToken = cancellationToken)
        ).mapError { error ->
          PrivilegedActionError.ServerError(error)
        }.bind()

        fingerprintResetActionCache.value = null
      }
    }

  override suspend fun getLatestFingerprintResetAction(): Result<PrivilegedActionInstance?, PrivilegedActionError> {
    return getPrivilegedActionsByType(PrivilegedActionType.RESET_FINGERPRINT)
      .flatMap { actions ->
        // There should be at most one pending action for fingerprint reset
        when (actions.size) {
          0 -> Ok(null)
          1 -> Ok(actions.first().instance)
          else -> Err(PrivilegedActionError.MultiplePendingActionsFound)
        }
      }
      .also { result ->
        result.mapBoth(
          success = { instance -> fingerprintResetActionCache.value = instance },
          failure = {}
        )
      }
  }

  override suspend fun getPendingFingerprintResetGrant(): Result<Grant?, DbError> {
    return grantDao.getGrantByAction(GrantAction.FINGERPRINT_RESET)
  }

  override suspend fun deleteFingerprintResetGrant(): Result<Unit, DbError> {
    return grantDao.deleteGrantByAction(GrantAction.FINGERPRINT_RESET)
  }

  override suspend fun isGrantDelivered(): Boolean {
    return grantDao.getDeliveredStatus(GrantAction.FINGERPRINT_RESET)
      .get() ?: false
  }

  override suspend fun markGrantAsDelivered(): Result<Unit, DbError> {
    return grantDao.markAsDelivered(GrantAction.FINGERPRINT_RESET)
  }

  override suspend fun clearEnrolledFingerprints(): Result<Unit, Error> {
    return Ok(hardwareUnlockInfoService.clear())
  }

  override fun pendingFingerprintResetGrant(): Flow<Grant?> =
    grantDao.grantByAction(GrantAction.FINGERPRINT_RESET)

  override suspend fun getFingerprintResetState(): Result<FingerprintResetState, PrivilegedActionError> =
    coroutineBinding {
      val pendingAction = getLatestFingerprintResetAction().bind()

      when (val authStrategy = pendingAction?.authorizationStrategy) {
        is AuthorizationStrategy.DelayAndNotify -> {
          // Check if the delay period has completed
          if (pendingAction.isDelayAndNotifyReadyToComplete(clock)) {
            FingerprintResetState.DelayCompleted(pendingAction)
          } else {
            FingerprintResetState.DelayInProgress(pendingAction, authStrategy)
          }
        }
        else -> {
          // No server-side action, check for persisted grant
          val persistedGrant = getPendingFingerprintResetGrant()
            .mapError { dbError ->
              PrivilegedActionError.DatabaseError(dbError)
            }
            .bind()

          if (persistedGrant != null) {
            FingerprintResetState.GrantReady(persistedGrant)
          } else {
            FingerprintResetState.None
          }
        }
      }
    }

  private fun derEncodedSignature(signature: ByteArray): ByteString {
    require(signature.size == GRANT_SIGNATURE_LEN) {
      "Invalid signature length: expected $GRANT_SIGNATURE_LEN bytes, got ${signature.size}"
    }

    return signatureUtils.encodeSignatureToDer(signature)
  }
}
