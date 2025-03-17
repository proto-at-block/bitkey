package build.wallet.recovery

import bitkey.account.AccountConfigService
import bitkey.auth.AuthTokenScope.Global
import bitkey.f8e.error.F8eError.SpecificClientError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.NO_RECOVERY_EXISTS
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AuthTokensService
import build.wallet.auth.logAuthFailure
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.AuthF8eClient
import build.wallet.f8e.auth.AuthF8eClient.InitiateAuthenticationSuccess
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClient
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.notifications.DeviceTokenManager
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.CancelDelayNotifyRecoveryError.LocalCancelDelayNotifyError
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recoverIf
import kotlinx.coroutines.sync.withLock

@BitkeyInject(AppScope::class)
class LostAppAndCloudRecoveryServiceImpl(
  private val authF8eClient: AuthF8eClient,
  private val cancelDelayNotifyRecoveryF8eClient: CancelDelayNotifyRecoveryF8eClient,
  private val recoverySyncer: RecoverySyncer,
  private val recoveryLock: RecoveryLock,
  private val accountConfigService: AccountConfigService,
  private val accountAuthenticator: AccountAuthenticator,
  private val authTokensService: AuthTokensService,
  private val deviceTokenManager: DeviceTokenManager,
  private val appKeysGenerator: AppKeysGenerator,
  private val listKeysetsF8eClient: ListKeysetsF8eClient,
) : LostAppAndCloudRecoveryService {
  override suspend fun initiateAuth(
    hwAuthKey: HwAuthPublicKey,
  ): Result<InitiateAuthenticationSuccess, Error> {
    recoveryLock.withLock {
      val f8eEnvironment = accountConfigService.defaultConfig().value.f8eEnvironment
      return authF8eClient.initiateAuthentication(f8eEnvironment, hwAuthKey)
    }
  }

  override suspend fun completeAuth(
    accountId: FullAccountId,
    session: String,
    hwAuthKey: HwAuthPublicKey,
    hwSignedChallenge: String,
  ): Result<CompletedAuth, Throwable> =
    coroutineBinding {
      recoveryLock.withLock {
        // Authenticate with F8E
        val authTokens = accountAuthenticator
          .hwAuth(accountId, session, hwSignedChallenge)
          .logAuthFailure { "Failed to authenticate for lost app recovery" }
          .bind()

        // Store auth tokens
        authTokensService.setTokens(accountId, authTokens, Global).bind()

        // send in device-token for notifications
        // TODO(W-3372): validate result of this method
        deviceTokenManager.addDeviceTokenIfPresentForAccount(accountId, Global)

        val accountConfig = accountConfigService.defaultConfig().value
        // Get existing keysets
        val keysets = listKeysetsF8eClient
          .listKeysets(accountConfig.f8eEnvironment, accountId)
          .bind()

        val destinationAppKeys = appKeysGenerator
          .generateKeyBundle()
          .bind()

        CompletedAuth(
          accountId = accountId,
          authTokens = authTokens,
          hwAuthKey = hwAuthKey,
          destinationAppKeys = destinationAppKeys,
          existingHwSpendingKeys = keysets.map { it.hardwareKey }
        )
      }
    }

  override suspend fun cancelRecovery(
    accountId: FullAccountId,
    hwProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, CancelDelayNotifyRecoveryError> =
    coroutineBinding {
      recoveryLock.withLock {
        val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
        cancelDelayNotifyRecoveryF8eClient
          .cancel(
            f8eEnvironment = f8eEnvironment,
            fullAccountId = accountId,
            hwFactorProofOfPossession = hwProofOfPossession
          )
          .recoverIf(
            predicate = { f8eError ->
              // We expect to get a 4xx NO_RECOVERY_EXISTS error if we try to cancel
              // a recovery that has already been canceled. In that case, treat it as
              // a success, so we will still proceed below and delete the stored recovery
              val clientError = f8eError as? SpecificClientError<CancelDelayNotifyRecoveryErrorCode>
              clientError?.errorCode == NO_RECOVERY_EXISTS
            },
            transform = {}
          )
          .mapError(::F8eCancelDelayNotifyError)
          .bind()

        recoverySyncer.clear()
          .mapError(::LocalCancelDelayNotifyError)
          .bind()
      }
    }
}
