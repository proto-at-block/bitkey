package build.wallet.auth

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploader
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.recovery.RotateAuthKeysF8eClient
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.*
import build.wallet.mapUnit
import build.wallet.relationships.EndorseTrustedContactsService
import build.wallet.relationships.RelationshipsService
import build.wallet.relationships.syncAndVerifyRelationships
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class FullAccountAuthKeyRotationServiceImpl(
  private val authKeyRotationAttemptDao: AuthKeyRotationAttemptDao,
  private val rotateAuthKeysF8eClient: RotateAuthKeysF8eClient,
  private val keyboxDao: KeyboxDao,
  private val accountAuthenticator: AccountAuthenticator,
  private val bestEffortFullAccountCloudBackupUploader: BestEffortFullAccountCloudBackupUploader,
  private val relationshipsService: RelationshipsService,
  private val endorseTrustedContactsService: EndorseTrustedContactsService,
) : FullAccountAuthKeyRotationService {
  private val pendingRotationAttemptChangedSemaphore = MutableSharedFlow<Unit>(
    // Although 0 is default, let's be explicit here. We don't want to replay any events to new subscribers.
    replay = 0,
    // But for existing subscribers, we want to make sure they get notified even if they can't keep up.
    extraBufferCapacity = 1,
    // For good measure, let's drop the oldest event if the subscriber can't keep up.
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  override fun observePendingKeyRotationAttemptUntilNull(): Flow<PendingAuthKeyRotationAttempt?> {
    return flow {
      do {
        val attempt = getPendingRotationAttemptOrNull()

        emit(attempt)

        // If there is a pending attempt, we'll wait for it to change before checking again.
        if (attempt != null) {
          pendingRotationAttemptChangedSemaphore.first()
        }
      } while (attempt != null)
    }
  }

  override suspend fun startOrResumeAuthKeyRotation(
    request: AuthKeyRotationRequest,
    account: FullAccount,
  ): AuthKeyRotationResult =
    coroutineBinding {
      val resumeRequest = createAndSubmitKeysIfNeeded(request, account.keybox).bind()

      // Any unexpected error will be propagated by the .bind() call.
      when (val newKeysValidationResult = validateNewKeys(resumeRequest, account).bind()) {
        // New keys are valid, store them and we're done.
        is NewKeyValidationSuccessOrContinue.NewKeysValid -> {
          rotateKeysLocally(newKeysValidationResult.newKeys, account)
            .onSuccess {
              logDebug { "Rotated keys authed successfully" }
            }
            .onFailure {
              logWarn { "Rotated keys failed to auth" }
            }
            .bind()
        }
        // New keys are rejected by backend, time to check if old keys still work.
        NewKeyValidationSuccessOrContinue.NewKeysInvalid -> {
          validateOldKeys(resumeRequest, account.keybox)
            .onSuccess {
              logDebug { "Pre-rotation keys auth successfully" }
            }
            .onFailure {
              logWarn { "Pre-rotation keys failed to auth" }
            }
            .bind<Nothing>()
        }
      }
    }

  private suspend fun createAndSubmitKeysIfNeeded(
    request: AuthKeyRotationRequest,
    keyboxToRotate: Keybox,
  ): Result<AuthKeyRotationRequest.Resume, AuthKeyRotationFailure> =
    coroutineBinding {
      when (request) {
        is AuthKeyRotationRequest.Resume -> request
        is AuthKeyRotationRequest.Start -> {
          authKeyRotationAttemptDao
            .setAuthKeysWritten(request.newKeys)
            .mapError {
              AuthKeyRotationFailure.Acceptable(
                onAcknowledge = {
                  notifyPendingRotationAttemptChanged()
                }
              )
            }
            .bind()

          // Do NOT .bind(). We don't care about the response here,
          // as we'll go and validate the new keys regardless.
          rotateKeysOnServer(
            request = request,
            keyboxToRotate = keyboxToRotate
          ).logFailure {
            "Error rotating keys on server. We'll still try to validate the new keys."
          }.onSuccess {
            logDebug { "Rotated auth keys on server" }
          }

          AuthKeyRotationRequest.Resume(request.newKeys)
        }
      }
    }

  private suspend fun validateNewKeys(
    request: AuthKeyRotationRequest.Resume,
    account: FullAccount,
  ): Result<NewKeyValidationSuccessOrContinue, AuthKeyRotationFailure.Unexpected> {
    val keysetValidationResult = validateKeySet(
      f8eEnvironment = account.keybox.config.f8eEnvironment,
      appGlobalAuthPublicKey = request.newKeys.appGlobalAuthPublicKey,
      appRecoveryAuthPublicKey = request.newKeys.appRecoveryAuthPublicKey
    )

    return keysetValidationResult.flatMapEither(
      success = { Ok(NewKeyValidationSuccessOrContinue.NewKeysValid(request.newKeys)) },
      failure = { error ->
        when (error) {
          KeySetValidationFailure.Invalid -> Ok(NewKeyValidationSuccessOrContinue.NewKeysInvalid)
          KeySetValidationFailure.Unexpected -> Err(AuthKeyRotationFailure.Unexpected(request))
        }
      }
    )
  }

  private suspend fun validateOldKeys(
    retryRequest: AuthKeyRotationRequest.Resume,
    keyboxToRotate: Keybox,
  ): Result<Nothing, AuthKeyRotationFailure> =
    coroutineBinding {
      validateKeySet(
        f8eEnvironment = keyboxToRotate.config.f8eEnvironment,
        appGlobalAuthPublicKey = keyboxToRotate.activeAppKeyBundle.authKey,
        appRecoveryAuthPublicKey = keyboxToRotate.activeAppKeyBundle.recoveryAuthKey
      ).mapError { error ->
        when (error) {
          KeySetValidationFailure.Invalid -> AuthKeyRotationFailure.AccountLocked(
            retryRequest = retryRequest
          )
          KeySetValidationFailure.Unexpected -> AuthKeyRotationFailure.Unexpected(
            retryRequest = retryRequest
          )
        }
      }.bind()

      consumePendingRotationAttempt()
        .mapError {
          AuthKeyRotationFailure.Unexpected(retryRequest = retryRequest)
        }
        .bind()

      Err(
        AuthKeyRotationFailure.Acceptable(
          onAcknowledge = {
            notifyPendingRotationAttemptChanged()
          }
        )
      ).bind<Nothing>()
    }

  private suspend fun validateKeySet(
    f8eEnvironment: F8eEnvironment,
    appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    appRecoveryAuthPublicKey: PublicKey<AppRecoveryAuthKey>,
  ): Result<Unit, KeySetValidationFailure> =
    coroutineBinding {
      // TODO: What if just one of Global vs Recovery keys are invalid?
      accountAuthenticator.appAuth(
        f8eEnvironment = f8eEnvironment,
        appAuthPublicKey = appGlobalAuthPublicKey,
        authTokenScope = AuthTokenScope.Global
      ).mapAuthErrorToKeySetValidationFailure().bind()
      accountAuthenticator.appAuth(
        f8eEnvironment = f8eEnvironment,
        appAuthPublicKey = appRecoveryAuthPublicKey,
        authTokenScope = AuthTokenScope.Recovery
      ).mapAuthErrorToKeySetValidationFailure().bind()
    }

  override suspend fun recommendKeyRotation() {
    authKeyRotationAttemptDao.setKeyRotationProposal()
  }

  override suspend fun dismissProposedRotationAttempt() {
    if (getPendingRotationAttemptOrNull() == PendingAuthKeyRotationAttempt.ProposedAttempt) {
      consumePendingRotationAttempt()
      notifyPendingRotationAttemptChanged()
    }
  }

  private suspend fun getPendingRotationAttemptOrNull(): PendingAuthKeyRotationAttempt? {
    val pendingAttemptDaoState = authKeyRotationAttemptDao.observeAuthKeyRotationAttemptState()
      .firstOrNull()
      ?.get()

    return when (pendingAttemptDaoState) {
      is AuthKeyRotationAttemptDaoState.AuthKeysWritten -> PendingAuthKeyRotationAttempt.IncompleteAttempt(
        newKeys = pendingAttemptDaoState.appAuthPublicKeys
      )
      AuthKeyRotationAttemptDaoState.KeyRotationProposalWritten -> PendingAuthKeyRotationAttempt.ProposedAttempt
      AuthKeyRotationAttemptDaoState.NoAttemptInProgress, null -> null
    }
  }

  /**
   * If there was a pending rotation attempt, this will remove it from DAO.
   * That way we don't show the rotation flow to the user the next time they open the app.
   */
  private suspend fun consumePendingRotationAttempt(): Result<Unit, Throwable> {
    return authKeyRotationAttemptDao.clear()
  }

  /**
   * Once we're done with the pending rotation attempt, we want to clear it from memory.
   * Note that this doesn't remove it from the DAO, that's done in [consumePendingRotationAttempt].
   * If we just clear it from memory, we'll show the rotation flow to the user again next time they open the app.
   */
  private fun notifyPendingRotationAttemptChanged() {
    pendingRotationAttemptChangedSemaphore.tryEmit(Unit)
  }

  private suspend fun rotateKeysOnServer(
    request: AuthKeyRotationRequest.Start,
    keyboxToRotate: Keybox,
  ): Result<Unit, Throwable> {
    return rotateAuthKeysF8eClient.rotateKeyset(
      f8eEnvironment = keyboxToRotate.config.f8eEnvironment,
      fullAccountId = keyboxToRotate.fullAccountId,
      newAppAuthPublicKeys = request.newKeys,
      oldAppAuthPublicKey = keyboxToRotate.activeAppKeyBundle.authKey,
      hwAuthPublicKey = request.hwAuthPublicKey,
      hwSignedAccountId = request.hwSignedAccountId,
      hwFactorProofOfPossession = request.hwFactorProofOfPossession
    )
  }

  private suspend fun rotateKeysLocally(
    newKeys: AppAuthPublicKeys,
    account: FullAccount,
  ): Result<AuthKeyRotationSuccess, AuthKeyRotationFailure.Unexpected> =
    coroutineBinding {
      val rotatedKeybox = keyboxDao.rotateKeyboxAuthKeys(account.keybox, newKeys)
        .mapError {
          AuthKeyRotationFailure.Unexpected(
            retryRequest = AuthKeyRotationRequest.Resume(newKeys)
          )
        }
        .bind()

      // TODO(BKR-892): We shouldn't just copy the account to update the keybox in it.
      //  Instead this should be handled by Cloud Backup automatically for us in the future.
      val rotatedAccount = account.copy(
        keybox = rotatedKeybox
      )

      // With new auth keys, we need to re-generate new endorsement certificates for existing
      // trusted contacts.
      regenerateEndorseAndVerifyTrustedContacts(
        newAccount = rotatedAccount,
        oldHwAuthPublicKey = account.keybox.activeHwKeyBundle.authKey,
        oldAppAuthKey = account.keybox.activeAppKeyBundle.authKey,
        newAppKeys = newKeys
      )
        .mapError {
          AuthKeyRotationFailure.Unexpected(
            retryRequest = AuthKeyRotationRequest.Resume(newKeys)
          )
        }
        .bind()

      trySynchronizingCloudBackup(rotatedAccount)
        .mapError {
          AuthKeyRotationFailure.Unexpected(
            retryRequest = AuthKeyRotationRequest.Resume(newKeys)
          )
        }
        .onSuccess {
          logInfo { "Cloud backup uploaded after rotating auth keys" }
        }
        .bind()

      consumePendingRotationAttempt().mapError {
        AuthKeyRotationFailure.Unexpected(
          retryRequest = AuthKeyRotationRequest.Resume(newKeys)
        )
      }.bind()

      AuthKeyRotationSuccess(
        onAcknowledge = {
          notifyPendingRotationAttemptChanged()
        }
      )
    }

  private suspend fun regenerateEndorseAndVerifyTrustedContacts(
    newAccount: FullAccount,
    oldAppAuthKey: PublicKey<AppGlobalAuthKey>,
    oldHwAuthPublicKey: HwAuthPublicKey,
    newAppKeys: AppAuthPublicKeys,
  ): Result<Unit, Error> =
    coroutineBinding {
      val relationships = relationshipsService.relationships
        .filterNotNull()
        .first()
      endorseTrustedContactsService.authenticateRegenerateAndEndorse(
        accountId = newAccount.accountId,
        f8eEnvironment = newAccount.config.f8eEnvironment,
        contacts = relationships.endorsedTrustedContacts,
        oldAppGlobalAuthKey = oldAppAuthKey,
        oldHwAuthKey = oldHwAuthPublicKey,
        newAppGlobalAuthKey = newAppKeys.appGlobalAuthPublicKey,
        newAppGlobalAuthKeyHwSignature = newAppKeys.appGlobalAuthKeyHwSignature
      ).bind()

      relationshipsService.syncAndVerifyRelationships(newAccount).bind()
    }

  private suspend fun trySynchronizingCloudBackup(account: FullAccount): Result<Unit, Error> {
    return bestEffortFullAccountCloudBackupUploader
      .createAndUploadCloudBackup(
        fullAccount = account
      )
      .recoverIf(
        predicate = {
          // Ignore the IgnorableErrors
          it is BestEffortFullAccountCloudBackupUploader.Failure.IgnorableError
        },
        transform = {
          logInternal(
            level = LogLevel.Debug,
            throwable = it
          ) { "Ignoring cloud backup error" }
        }
      )
      .logFailure { "Could not upload cloud backup after rotating auth keys" }
  }

  private fun Result<AccountAuthenticator.AuthData, AuthError>.mapAuthErrorToKeySetValidationFailure(): Result<Unit, KeySetValidationFailure> {
    return mapUnit().mapError { error ->
      when (error) {
        AuthSignatureMismatch, is AuthProtocolError -> KeySetValidationFailure.Invalid
        else -> KeySetValidationFailure.Unexpected
      }
    }
  }

  private sealed interface KeySetValidationFailure {
    data object Unexpected : KeySetValidationFailure

    data object Invalid : KeySetValidationFailure
  }

  private sealed interface NewKeyValidationSuccessOrContinue {
    data class NewKeysValid(
      val newKeys: AppAuthPublicKeys,
    ) : NewKeyValidationSuccessOrContinue

    data object NewKeysInvalid : NewKeyValidationSuccessOrContinue
  }
}
