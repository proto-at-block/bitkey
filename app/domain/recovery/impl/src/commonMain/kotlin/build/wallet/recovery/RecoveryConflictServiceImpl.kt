package build.wallet.recovery

import bitkey.account.AccountConfigService
import bitkey.account.isW3Hardware
import bitkey.auth.AuthTokenScope
import bitkey.f8e.error.F8eError.SpecificClientError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.NO_RECOVERY_EXISTS
import bitkey.privilegedactions.ActionProofService
import bitkey.recovery.RecoveryStatusService
import build.wallet.account.AccountService
import build.wallet.auth.AuthTokensService
import build.wallet.account.getAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClient
import build.wallet.logging.logFailure
import build.wallet.recovery.RecoveryConflictServiceError.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recoverIf
import uniffi.actionproof.Action

@BitkeyInject(AppScope::class)
class RecoveryConflictServiceImpl(
  private val cancelDelayNotifyRecoveryF8eClient: CancelDelayNotifyRecoveryF8eClient,
  private val recoveryStatusService: RecoveryStatusService,
  private val accountConfigService: AccountConfigService,
  private val accountService: AccountService,
  private val actionProofService: ActionProofService,
  private val authTokensService: AuthTokensService,
) : RecoveryConflictService {
  override suspend fun cancelRecoveryConflict(
    fullAccountId: FullAccountId,
  ): Result<Unit, RecoveryConflictServiceError> =
    coroutineBinding {
      val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment

      // Build app-signed CancelConflictingRecovery action proof for W3 accounts.
      // Refresh the access token first so the token binding in the proof
      // matches the JWT the HTTP client will send.
      val proof = if (accountConfigService.activeOrDefaultConfig().value.isW3Hardware) {
        val account = accountService.getAccount<FullAccount>()
          .mapError(::LocalCancelRecoveryConflictError)
          .bind()
        // Refresh token so the binding matches the token sent with the f8e request.
        authTokensService.refreshAccessTokenWithApp(
          f8eEnvironment = f8eEnvironment,
          accountId = account.accountId,
          scope = AuthTokenScope.Global
        ).mapError { LocalCancelRecoveryConflictError(it) }.bind()

        val header = actionProofService.createAppSignedHeader(
          action = Action.CANCEL_CONFLICTING_RECOVERY,
          appAuthKey = account.keybox.activeAppKeyBundle.authKey
        ).logFailure { "Failed to build action proof for cancel conflicting recovery" }
          .mapError { LocalCancelRecoveryConflictError(it) }
          .bind()
        PrivilegedActionProof.AppSignedAction(header)
      } else {
        null
      }

      cancelDelayNotifyRecoveryF8eClient.cancel(
        f8eEnvironment = f8eEnvironment,
        fullAccountId = fullAccountId,
        proof = proof
      ).recoverIf(
        predicate = { f8eError ->
          val clientError = f8eError as? SpecificClientError<CancelDelayNotifyRecoveryErrorCode>
          clientError?.errorCode == NO_RECOVERY_EXISTS
        },
        transform = {}
      ).mapError {
        val specificClientError = it as? SpecificClientError<CancelDelayNotifyRecoveryErrorCode>
        when {
          specificClientError?.errorCode == COMMS_VERIFICATION_REQUIRED ->
            CommsVerificationRequired(it.error)
          else -> F8eCancelRecoveryConflictError(it)
        }
      }.bind()

      recoveryStatusService.clear()
        .mapError(::LocalCancelRecoveryConflictError)
        .bind()
    }
}
