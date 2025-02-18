package build.wallet.recovery

import build.wallet.account.AccountService
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AccountCreationError.AccountCreationDatabaseError.FailedToSaveAuthTokens
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokenScope.Global
import build.wallet.auth.AuthTokenScope.Recovery
import build.wallet.auth.AuthTokensService
import build.wallet.auth.signChallenge
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.challange.SignedChallenge.HardwareSignedChallenge
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.recovery.CompleteDelayNotifyF8eClient
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logFailure
import build.wallet.logging.logNetworkFailure
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class RecoveryAuthCompleterImpl(
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val completeDelayNotifyF8eClient: CompleteDelayNotifyF8eClient,
  private val accountAuthenticator: AccountAuthenticator,
  private val recoverySyncer: RecoverySyncer,
  private val authTokensService: AuthTokensService,
  private val accountService: AccountService,
  private val keyboxDao: KeyboxDao,
  private val recoveryLock: RecoveryLock,
) : RecoveryAuthCompleter {
  override suspend fun rotateAuthKeys(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hardwareSignedChallenge: HardwareSignedChallenge,
    destinationAppAuthPubKeys: AppAuthPublicKeys,
    sealedCsek: SealedCsek,
  ): Result<Unit, Throwable> {
    return recoveryLock.withLock {
      coroutineBinding {
        // Hack for W-4377; this entire method needs to take at least 2 seconds, so the last step
        // is performed after this minimum delay because it triggers recompose via recovery change.
        withMinimumDelay(2.seconds) {
          ensure(hardwareSignedChallenge.signingFactor == Hardware) {
            Error("Expected $hardwareSignedChallenge to be signed with Hardware factor.")
          }

          recoverySyncer
            .setLocalRecoveryProgress(
              LocalRecoveryAttemptProgress.AttemptingCompletion(sealedCsek = sealedCsek)
            ).bind()

          val appSignedChallenge =
            appAuthKeyMessageSigner
              .signChallenge(
                publicKey = destinationAppAuthPubKeys.appGlobalAuthPublicKey,
                challenge = hardwareSignedChallenge.challenge
              )
              .logFailure { "Error signing complete recovery challenge with app auth key." }
              .bind()

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
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    destinationAppAuthPubKeys: AppAuthPublicKeys,
  ): Result<Unit, Throwable> {
    return recoveryLock.withLock {
      coroutineBinding {
        rotateAuthKeysAndTokens(
          f8eEnvironment = f8eEnvironment,
          accountId = fullAccountId,
          newAuthKeys = destinationAppAuthPubKeys
        ).bind()

        recoverySyncer
          .setLocalRecoveryProgress(
            LocalRecoveryAttemptProgress.RotatedAuthKeys
          ).bind()
      }
    }
  }

  private suspend fun rotateAuthKeysAndTokens(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    newAuthKeys: AppAuthPublicKeys,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      // Verify auth keys by getting new auth tokens
      val globalAuthTokens = accountAuthenticator.appAuth(
        f8eEnvironment = f8eEnvironment,
        appAuthPublicKey = newAuthKeys.appGlobalAuthPublicKey,
        authTokenScope = Global
      ).bind().authTokens
      val recoveryAuthTokens = accountAuthenticator.appAuth(
        f8eEnvironment = f8eEnvironment,
        appAuthPublicKey = newAuthKeys.appRecoveryAuthPublicKey,
        authTokenScope = Recovery
      ).bind().authTokens

      val account = accountService.activeAccount().first()
      if (account != null) {
        ensure(account is FullAccount) { Error("Expected Full Account but got ${account::class}") }
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
