package build.wallet.recovery

import bitkey.account.AccountConfigService
import bitkey.auth.AuthTokenScope.Global
import bitkey.auth.AuthTokenScope.Recovery
import bitkey.onboarding.LiteAccountCreationError.LiteAccountCreationDatabaseError.FailedToSaveAuthTokens
import bitkey.recovery.RecoveryStatusService
import build.wallet.account.AccountService
import build.wallet.account.getAccountOrNull
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokensService
import build.wallet.auth.signChallenge
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.challange.SignedChallenge.HardwareSignedChallenge
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.f8e.recovery.CompleteDelayNotifyF8eClient
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logFailure
import build.wallet.logging.logNetworkFailure
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class RecoveryAuthCompleterImpl(
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val completeDelayNotifyF8eClient: CompleteDelayNotifyF8eClient,
  private val accountAuthenticator: AccountAuthenticator,
  private val recoveryStatusService: RecoveryStatusService,
  private val authTokensService: AuthTokensService,
  private val accountService: AccountService,
  private val keyboxDao: KeyboxDao,
  private val recoveryLock: RecoveryLock,
  private val accountConfigService: AccountConfigService,
) : RecoveryAuthCompleter {
  override suspend fun rotateAuthKeys(
    fullAccountId: FullAccountId,
    hardwareSignedChallenge: HardwareSignedChallenge,
    destinationAppAuthPubKeys: AppAuthPublicKeys,
    sealedCsek: SealedCsek,
    sealedSsek: SealedSsek,
  ): Result<Unit, Throwable> {
    return recoveryLock.withLock {
      coroutineBinding {
        // Hack for W-4377; this entire method needs to take at least 2 seconds, so the last step
        // is performed after this minimum delay because it triggers recompose via recovery change.
        withMinimumDelay(2.seconds) {
          ensure(hardwareSignedChallenge.signingFactor == Hardware) {
            Error("Expected $hardwareSignedChallenge to be signed with Hardware factor.")
          }

          recoveryStatusService
            .setLocalRecoveryProgress(
              LocalRecoveryAttemptProgress.AttemptingCompletion(sealedCsek = sealedCsek, sealedSsek = sealedSsek)
            ).bind()

          val appSignedChallenge =
            appAuthKeyMessageSigner
              .signChallenge(
                publicKey = destinationAppAuthPubKeys.appGlobalAuthPublicKey,
                challenge = hardwareSignedChallenge.challenge
              )
              .logFailure { "Error signing complete recovery challenge with app auth key." }
              .bind()

          val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
          completeDelayNotifyF8eClient
            .complete(
              f8eEnvironment = f8eEnvironment,
              fullAccountId = fullAccountId,
              challenge = hardwareSignedChallenge.challenge.data,
              appSignature = appSignedChallenge.signature,
              hardwareSignature = hardwareSignedChallenge.signature
            )
            .logNetworkFailure { "Error completing recovery with f8e." }
            .bind()
        }
      }
    }
  }

  override suspend fun rotateAuthTokens(
    fullAccountId: FullAccountId,
    destinationAppAuthPubKeys: AppAuthPublicKeys,
  ): Result<Unit, Throwable> {
    return recoveryLock.withLock {
      coroutineBinding {
        rotateAuthKeysAndTokens(
          accountId = fullAccountId,
          newAuthKeys = destinationAppAuthPubKeys
        ).bind()

        recoveryStatusService
          .setLocalRecoveryProgress(
            LocalRecoveryAttemptProgress.RotatedAuthKeys
          ).bind()
      }
    }
  }

  private suspend fun rotateAuthKeysAndTokens(
    accountId: FullAccountId,
    newAuthKeys: AppAuthPublicKeys,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      // Verify auth keys by getting new auth tokens
      val globalAuthTokens = accountAuthenticator.appAuth(
        appAuthPublicKey = newAuthKeys.appGlobalAuthPublicKey,
        authTokenScope = Global
      ).bind().authTokens
      val recoveryAuthTokens = accountAuthenticator.appAuth(
        appAuthPublicKey = newAuthKeys.appRecoveryAuthPublicKey,
        authTokenScope = Recovery
      ).bind().authTokens

      val account = accountService.getAccountOrNull<FullAccount>().bind()
      if (account != null) {
        // We have an active Full Account indicating a Lost Hardware recovery.
        // In this case, we need to rotate the auth keys in the active keybox.
        val oldKeybox = account.keybox
        keyboxDao.rotateKeyboxAuthKeys(oldKeybox, newAuthKeys).bind()
      }

      // Save new auth tokens after auth keys have been successfully rotated
      authTokensService
        .setTokens(accountId, globalAuthTokens, Global)
        .mapError(::FailedToSaveAuthTokens)
        .bind()
      authTokensService
        .setTokens(accountId, recoveryAuthTokens, Recovery)
        .mapError(::FailedToSaveAuthTokens)
        .bind()
    }
}
