package build.wallet.recovery

import build.wallet.auth.AccountAuthTokens
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AccountCreationError
import build.wallet.auth.AccountCreationError.AccountCreationAuthError
import build.wallet.auth.AccountCreationError.AccountCreationDatabaseError.FailedToSaveAuthTokens
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokenDao
import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.recovery.CompleteDelayNotifyService
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.logging.logNetworkFailure
import build.wallet.recovery.socrec.SocRecRelationshipsRepository
import build.wallet.time.Delayer
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import kotlin.time.Duration.Companion.seconds

class RecoveryAuthCompleterImpl(
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val completeDelayNotifyService: CompleteDelayNotifyService,
  private val accountAuthenticator: AccountAuthenticator,
  private val recoverySyncer: RecoverySyncer,
  private val authTokenDao: AuthTokenDao,
  private val socRecRelationshipsRepository: SocRecRelationshipsRepository,
  private val delayer: Delayer,
) : RecoveryAuthCompleter {
  override suspend fun rotateAuthKeys(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    challenge: ChallengeToCompleteRecovery,
    hardwareSignedChallenge: SignedChallengeToCompleteRecovery,
    destinationAppAuthPubKeys: AppAuthPublicKeys,
    sealedCsek: SealedCsek,
    removeProtectedCustomers: Boolean,
  ): Result<Unit, Throwable> {
    log { "Rotating auth keys for recovery" }

    return binding {
      // Hack for W-4377; this entire method needs to take at least 2 seconds, so the last step
      // is performed after this minimum delay because it triggers recompose via recovery change.
      delayer.withMinimumDelay(2.seconds) {
        if (hardwareSignedChallenge.signingFactor != Hardware) {
          Err(Error("Expected $hardwareSignedChallenge to be signed with Hardware factor."))
            .bind<AccountAuthTokens>()
        }

        recoverySyncer
          .setLocalRecoveryProgress(
            LocalRecoveryAttemptProgress.AttemptingCompletion(sealedCsek = sealedCsek)
          ).bind()

        val signedCompleteRecoveryChallenge =
          appAuthKeyMessageSigner
            .signMessage(
              publicKey = destinationAppAuthPubKeys.appGlobalAuthPublicKey,
              message = challenge.bytes
            )
            .logFailure { "Error signing complete recovery challenge with app auth key." }
            .bind()

        completeDelayNotifyService
          .complete(
            f8eEnvironment = f8eEnvironment,
            fullAccountId = fullAccountId,
            challenge = challenge.bytes.utf8(),
            appSignature = signedCompleteRecoveryChallenge,
            hardwareSignature = hardwareSignedChallenge.signature
          )
          .logNetworkFailure { "Error completing recovery with f8e." }
          .bind()

        // TODO(W-4259): Move this out of here since it should come after completion.
        authenticateWithF8eAndStoreAuthTokens(
          accountId = fullAccountId,
          appAuthPublicKey = destinationAppAuthPubKeys.appRecoveryAuthPublicKey,
          f8eEnvironment = f8eEnvironment,
          tokenScope = AuthTokenScope.Recovery
        ).bind()
        authenticateWithF8eAndStoreAuthTokens(
          accountId = fullAccountId,
          appAuthPublicKey = destinationAppAuthPubKeys.appGlobalAuthPublicKey,
          f8eEnvironment = f8eEnvironment,
          tokenScope = AuthTokenScope.Global
        ).bind()

        if (removeProtectedCustomers) {
          socRecRelationshipsRepository
            .getRelationshipsWithoutSyncing(
              fullAccountId,
              f8eEnvironment
            )
            .protectedCustomers
            .onEach {
              socRecRelationshipsRepository.removeRelationshipWithoutSyncing(
                accountId = fullAccountId,
                f8eEnvironment = f8eEnvironment,
                hardwareProofOfPossession = null,
                AuthTokenScope.Recovery,
                it.recoveryRelationshipId
              ).bind()
            }
        }
      }

      recoverySyncer
        .setLocalRecoveryProgress(
          LocalRecoveryAttemptProgress.RotatedAuthKeys
        ).bind()
    }
  }

  /**
   * Performs auth with f8e using the given [AppAuthPublicKey] and stores the resulting
   * tokens in [AuthTokenDao] keyed by the given [AuthTokenScope]
   */
  private suspend fun authenticateWithF8eAndStoreAuthTokens(
    accountId: FullAccountId,
    appAuthPublicKey: PublicKey<out AppAuthKey>,
    f8eEnvironment: F8eEnvironment,
    tokenScope: AuthTokenScope,
  ): Result<Unit, AccountCreationError> {
    return binding {
      val authTokens =
        accountAuthenticator
          .appAuth(
            f8eEnvironment = f8eEnvironment,
            appAuthPublicKey = appAuthPublicKey,
            authTokenScope = tokenScope
          )
          .mapError { AccountCreationAuthError(it) }
          .bind()
          .authTokens

      authTokenDao
        .setTokensOfScope(accountId, authTokens, tokenScope)
        .mapError { FailedToSaveAuthTokens(it) }
        .bind()
    }
  }
}
