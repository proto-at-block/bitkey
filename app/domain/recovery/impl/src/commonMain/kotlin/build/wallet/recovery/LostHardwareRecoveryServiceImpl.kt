package build.wallet.recovery

import bitkey.account.HardwareType
import bitkey.account.isW3Hardware
import bitkey.auth.AuthTokenScope
import bitkey.f8e.error.F8eError.SpecificClientError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.NO_RECOVERY_EXISTS
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode.COMMS_VERIFICATION_REQUIRED
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode.RECOVERY_ALREADY_EXISTS
import bitkey.privilegedactions.ActionProofService
import bitkey.recovery.InitiateDelayNotifyRecoveryError
import bitkey.recovery.InitiateDelayNotifyRecoveryError.*
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.auth.AuthTokensService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingKeyProof
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClient
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyF8eClient
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.logging.logFailure
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.CancelDelayNotifyRecoveryError.LocalCancelDelayNotifyError
import build.wallet.recovery.LocalRecoveryAttemptProgress.CreatedPendingKeybundles
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recoverIf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.actionproof.Action

@BitkeyInject(AppScope::class)
class LostHardwareRecoveryServiceImpl(
  private val cancelDelayNotifyRecoveryF8eClient: CancelDelayNotifyRecoveryF8eClient,
  private val recoveryLock: RecoveryLock,
  private val initiateAccountDelayNotifyF8eClient: InitiateAccountDelayNotifyF8eClient,
  private val recoveryDao: RecoveryDao,
  private val accountService: AccountService,
  private val appKeysGenerator: AppKeysGenerator,
  private val actionProofService: ActionProofService,
  private val authTokensService: AuthTokensService,
) : LostHardwareRecoveryService {
  override suspend fun generateNewAppKeys(): Result<AppKeyBundle, Throwable> {
    return withContext(Dispatchers.Default) {
      appKeysGenerator.generateKeyBundle()
    }
  }

  override suspend fun initiate(
    destinationAppKeyBundle: AppKeyBundle,
    destinationHardwareKeyBundle: HwKeyBundle,
    spendingKeyProof: HwSpendingKeyProof?,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    hardwareType: HardwareType,
  ): Result<Unit, InitiateDelayNotifyRecoveryError> =
    coroutineBinding {
      recoveryLock.withLock {
        val account = accountService.getAccount<FullAccount>()
          .mapError(::OtherError)
          .bind()

        // Persist local pending recovery state
        recoveryDao.setLocalRecoveryProgress(
          CreatedPendingKeybundles(
            fullAccountId = account.accountId,
            appKeyBundle = destinationAppKeyBundle,
            hwKeyBundle = destinationHardwareKeyBundle,
            appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
            lostFactor = Hardware,
            originalAppGlobalAuthKey = account.keybox.activeAppKeyBundle.authKey,
            spendingKeyProof = spendingKeyProof
          )
        ).mapError { OtherError(it) }

        // Build app-signed action proof for W3 accounts
        val proof = if (account.config.isW3Hardware) {
          // Refresh token so the binding matches the token sent with the f8e request.
          authTokensService.refreshAccessTokenWithApp(
            f8eEnvironment = account.config.f8eEnvironment,
            accountId = account.accountId,
            scope = AuthTokenScope.Global
          ).mapError { OtherError(it) }.bind()

          val header = actionProofService.createAppSignedHeader(
            action = Action.CREATE_LOST_HARDWARE_RECOVERY,
            appAuthKey = account.keybox.activeAppKeyBundle.authKey
          ).logFailure { "Failed to build action proof for lost HW recovery" }
            .mapError { OtherError(it) }
            .bind()
          PrivilegedActionProof.AppSignedAction(header)
        } else {
          null
        }

        // Initiate delay period with f8e
        val serviceResponse =
          initiateAccountDelayNotifyF8eClient.initiate(
            f8eEnvironment = account.config.f8eEnvironment,
            fullAccountId = account.accountId,
            lostFactor = Hardware,
            appGlobalAuthKey = destinationAppKeyBundle.authKey,
            appRecoveryAuthKey = destinationAppKeyBundle.recoveryAuthKey,
            proof = proof,
            delayPeriod = account.config.delayNotifyDuration,
            hardwareAuthKey = destinationHardwareKeyBundle.authKey,
            hardwareType = hardwareType
          ).mapError {
            when (it) {
              is SpecificClientError<InitiateAccountDelayNotifyErrorCode> -> {
                when (it.errorCode) {
                  COMMS_VERIFICATION_REQUIRED -> CommsVerificationRequiredError(it.error)
                  RECOVERY_ALREADY_EXISTS -> RecoveryAlreadyExistsError(it.error)
                }
              }
              else -> OtherError(it.error)
            }
          }.bind()

        recoveryDao.setActiveServerRecovery(serviceResponse.serverRecovery)
          .mapError { OtherError(it) }
          .bind()
      }
    }

  override suspend fun cancelRecovery(): Result<Unit, CancelDelayNotifyRecoveryError> =
    coroutineBinding {
      recoveryLock.withLock {
        val account = accountService.getAccount<FullAccount>()
          .mapError(::LocalCancelDelayNotifyError)
          .bind()

        // Build app-signed action proof for W3 accounts
        val proof = if (account.config.isW3Hardware) {
          // Refresh token so the binding matches the token sent with the f8e request.
          authTokensService.refreshAccessTokenWithApp(
            f8eEnvironment = account.config.f8eEnvironment,
            accountId = account.accountId,
            scope = AuthTokenScope.Global
          ).mapError { LocalCancelDelayNotifyError(it) }.bind()

          val header = actionProofService.createAppSignedHeader(
            action = Action.CANCEL_LOST_HARDWARE_RECOVERY,
            appAuthKey = account.keybox.activeAppKeyBundle.authKey
          ).logFailure { "Failed to build action proof for cancel lost HW recovery" }
            .mapError { LocalCancelDelayNotifyError(it) }
            .bind()
          PrivilegedActionProof.AppSignedAction(header)
        } else {
          null
        }

        cancelDelayNotifyRecoveryF8eClient
          .cancel(
            f8eEnvironment = account.config.f8eEnvironment,
            fullAccountId = account.accountId,
            proof = proof
          )
          .recoverIf(
            predicate = { f8eError ->
              // We expect to get a 4xx NO_RECOVERY_EXISTS error if we try to cancel
              // a recovery that has already been canceled. In that case, treat it as
              // a success, so we will still proceed below and delete the stored recovery
              val clientError =
                f8eError as? SpecificClientError<CancelDelayNotifyRecoveryErrorCode>
              clientError?.errorCode == NO_RECOVERY_EXISTS
            },
            transform = {}
          )
          .mapError(::F8eCancelDelayNotifyError)
          .bind()

        recoveryDao.clear()
          .mapError(::LocalCancelDelayNotifyError)
          .bind()
      }
    }

  override suspend fun cancelConflictingRecovery(): Result<Unit, CancelDelayNotifyRecoveryError> =
    coroutineBinding {
      recoveryLock.withLock {
        val account = accountService.getAccount<FullAccount>()
          .mapError(::LocalCancelDelayNotifyError)
          .bind()

        // Build app-signed action proof for W3 accounts.
        // Refresh the access token first so the token binding in the proof
        // matches the JWT the HTTP client will send.
        val proof = if (account.config.isW3Hardware) {
          // Refresh token so the binding matches the token sent with the f8e request.
          authTokensService.refreshAccessTokenWithApp(
            f8eEnvironment = account.config.f8eEnvironment,
            accountId = account.accountId,
            scope = AuthTokenScope.Global
          ).mapError { LocalCancelDelayNotifyError(it) }.bind()

          val header = actionProofService.createAppSignedHeader(
            action = Action.CANCEL_CONFLICTING_RECOVERY,
            appAuthKey = account.keybox.activeAppKeyBundle.authKey
          ).logFailure { "Failed to build action proof for cancel conflicting recovery" }
            .mapError { LocalCancelDelayNotifyError(it) }
            .bind()
          PrivilegedActionProof.AppSignedAction(header)
        } else {
          null
        }

        cancelDelayNotifyRecoveryF8eClient
          .cancel(
            f8eEnvironment = account.config.f8eEnvironment,
            fullAccountId = account.accountId,
            proof = proof
          )
          .recoverIf(
            predicate = { f8eError ->
              // We expect to get a 4xx NO_RECOVERY_EXISTS error if we try to cancel
              // a recovery that has already been canceled. In that case, treat it as
              // a success, so we will still proceed below and delete the stored recovery
              val clientError =
                f8eError as? SpecificClientError<CancelDelayNotifyRecoveryErrorCode>
              clientError?.errorCode == NO_RECOVERY_EXISTS
            },
            transform = {}
          )
          .mapError(::F8eCancelDelayNotifyError)
          .bind()

        recoveryDao.clear()
          .mapError(::LocalCancelDelayNotifyError)
          .bind()
      }
    }
}
