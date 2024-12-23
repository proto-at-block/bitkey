package build.wallet.recovery

import build.wallet.account.AccountService
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AccountCreationError.AccountCreationDatabaseError.FailedToSaveAuthTokens
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokenDao
import build.wallet.auth.AuthTokenScope.Global
import build.wallet.auth.AuthTokenScope.Recovery
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
import build.wallet.relationships.RelationshipsService
import build.wallet.time.Delayer
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class RecoveryAuthCompleterImpl(
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val completeDelayNotifyF8eClient: CompleteDelayNotifyF8eClient,
  private val accountAuthenticator: AccountAuthenticator,
  private val relationshipsService: RelationshipsService,
  private val recoverySyncer: RecoverySyncer,
  private val authTokenDao: AuthTokenDao,
  private val delayer: Delayer,
  private val accountService: AccountService,
  private val keyboxDao: KeyboxDao,
) : RecoveryAuthCompleter {
  override suspend fun rotateAuthKeys(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hardwareSignedChallenge: HardwareSignedChallenge,
    destinationAppAuthPubKeys: AppAuthPublicKeys,
    sealedCsek: SealedCsek,
  ): Result<Unit, Throwable> {
    return coroutineBinding {
      // Hack for W-4377; this entire method needs to take at least 2 seconds, so the last step
      // is performed after this minimum delay because it triggers recompose via recovery change.
      delayer.withMinimumDelay(2.seconds) {
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

  override suspend fun rotateAuthTokens(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    destinationAppAuthPubKeys: AppAuthPublicKeys,
    removeTrustedContacts: Boolean,
  ): Result<Unit, Throwable> {
    return coroutineBinding {
      rotateAuthKeysAndTokens(
        f8eEnvironment = f8eEnvironment,
        accountId = fullAccountId,
        newAuthKeys = destinationAppAuthPubKeys
      ).bind()

      // We need to attempt to delete trusted contacts after rotating the auth tokens
      // so that the new tokens are used to authenticate the deletion. (The server has
      // already rotated the tokens, so the old tokens would be invalid.)
      if (removeTrustedContacts) {
        removeTrustedContacts(f8eEnvironment, fullAccountId).bind()
      }

      recoverySyncer
        .setLocalRecoveryProgress(
          LocalRecoveryAttemptProgress.RotatedAuthKeys
        ).bind()
    }
  }

  private suspend fun removeTrustedContacts(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      relationshipsService
        .getRelationshipsWithoutSyncing(fullAccountId, f8eEnvironment)
        .logFailure { "Error fetching relationships for removal" }
        .onSuccess { relationships ->
          relationships.protectedCustomers.onEach {
            relationshipsService.removeRelationshipWithoutSyncing(
              accountId = fullAccountId,
              f8eEnvironment = f8eEnvironment,
              hardwareProofOfPossession = null,
              Recovery,
              it.relationshipId
            ).bind()
          }
        }.bind()
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
      authTokenDao
        .setTokensOfScope(accountId, globalAuthTokens, Global)
        .mapError(::FailedToSaveAuthTokens)
        .bind()
      authTokenDao
        .setTokensOfScope(accountId, recoveryAuthTokens, Recovery)
        .mapError(::FailedToSaveAuthTokens)
        .bind()
    }
}
