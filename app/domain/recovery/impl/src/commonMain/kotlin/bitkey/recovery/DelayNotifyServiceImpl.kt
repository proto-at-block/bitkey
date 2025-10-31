package bitkey.recovery

import bitkey.account.AccountConfigService
import bitkey.auth.AuthTokenScope
import bitkey.auth.AuthTokenScope.Global
import bitkey.onboarding.LiteAccountCreationError.LiteAccountCreationDatabaseError.FailedToSaveAuthTokens
import bitkey.recovery.DelayNotifyCancellationRequest.CancelLostAppAndCloudRecovery
import bitkey.recovery.DelayNotifyCancellationRequest.CancelLostHardwareRecovery
import build.wallet.account.AccountService
import build.wallet.account.getAccountOrNull
import build.wallet.auth.*
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.challange.SignedChallenge.HardwareSignedChallenge
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.onboarding.CreateAccountKeysetF8eClient
import build.wallet.f8e.onboarding.CreateAccountKeysetV2F8eClient
import build.wallet.f8e.onboarding.SetActiveSpendingKeysetF8eClient
import build.wallet.f8e.recovery.CompleteDelayNotifyF8eClient
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.f8e.recovery.PrivateMultisigRemoteKeyset
import build.wallet.feature.flags.ChaincodeDelegationFeatureFlag
import build.wallet.feature.flags.UpdateToPrivateWalletOnRecoveryFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logFailure
import build.wallet.logging.logNetworkFailure
import build.wallet.notifications.DeviceTokenManager
import build.wallet.recovery.*
import build.wallet.relationships.EndorseTrustedContactsService
import build.wallet.relationships.RelationshipsService
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import bitkey.auth.AuthTokenScope.Recovery as RecoveryScope

@BitkeyInject(AppScope::class)
class DelayNotifyServiceImpl(
  private val lostAppAndCloudRecoveryService: LostAppAndCloudRecoveryService,
  private val lostHardwareRecoveryService: LostHardwareRecoveryService,
  private val recoveryStatusService: RecoveryStatusService,
  private val setActiveSpendingKeysetF8eClient: SetActiveSpendingKeysetF8eClient,
  private val accountConfigService: AccountConfigService,
  private val deviceTokenManager: DeviceTokenManager,
  private val createAccountKeysetF8eClient: CreateAccountKeysetF8eClient,
  private val createAccountKeysetV2F8eClient: CreateAccountKeysetV2F8eClient,
  private val chaincodeDelegationFeatureFlag: ChaincodeDelegationFeatureFlag,
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val completeDelayNotifyF8eClient: CompleteDelayNotifyF8eClient,
  private val accountAuthenticator: AccountAuthenticator,
  private val authTokensService: AuthTokensService,
  private val accountService: AccountService,
  private val keyboxDao: KeyboxDao,
  private val recoveryLock: RecoveryLock,
  private val relationshipsService: RelationshipsService,
  private val endorseTrustedContactsService: EndorseTrustedContactsService,
  private val updateToPrivateWalletOnRecoveryFeatureFlag:
    UpdateToPrivateWalletOnRecoveryFeatureFlag,
  private val listKeysetF8eClient: ListKeysetsF8eClient,
) : DelayNotifyService {
  override suspend fun cancelDelayNotify(
    request: DelayNotifyCancellationRequest,
  ): Result<Unit, Error> =
    coroutineBinding {
      val recovery = recoveryStatusService.status().first().bind()
      ensure(recovery is Recovery.StillRecovering) {
        Error("Recovery is not in progress")
      }
      when (request) {
        is CancelLostAppAndCloudRecovery -> lostAppAndCloudRecoveryService.cancelRecovery(
          accountId = recovery.fullAccountId,
          hwProofOfPossession = request.hwProofOfPossession
        )
        CancelLostHardwareRecovery -> lostHardwareRecoveryService.cancelRecovery()
      }.bind()
    }

  override suspend fun activateSpendingKeyset(
    keyset: F8eSpendingKeyset,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> =
    coroutineBinding {
      val recovery = recoveryStatusService.status().first().bind()
      ensure(recovery is Recovery.StillRecovering) {
        Error("Recovery is not in progress")
      }

      val config = accountConfigService.activeOrDefaultConfig().value
      setActiveSpendingKeysetF8eClient
        .set(
          f8eEnvironment = config.f8eEnvironment,
          fullAccountId = recovery.fullAccountId,
          keysetId = keyset.keysetId,
          appAuthKey = recovery.appGlobalAuthKey,
          hwFactorProofOfPossession = hardwareProofOfPossession
        )
        .bind()

      recoveryStatusService.setLocalRecoveryProgress(
        LocalRecoveryAttemptProgress.ActivatedSpendingKeys(
          f8eSpendingKeyset = keyset
        )
      )
        .bind()
    }

  override suspend fun createSpendingKeyset(
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<F8eSpendingKeyset, Error> =
    coroutineBinding {
      val recovery = recoveryStatusService.status().first().bind()
      ensure(recovery is Recovery.StillRecovering) {
        Error("Recovery is not in progress")
      }

      deviceTokenManager.addDeviceTokenIfPresentForAccount(
        fullAccountId = recovery.fullAccountId,
        authTokenScope = AuthTokenScope.Recovery
      ).result.logFailure {
        "Failed to add device token for account during Social Recovery"
      }

      val config = accountConfigService.activeOrDefaultConfig().value

      val hasPrivateKeyset =
        listKeysetF8eClient.listKeysets(config.f8eEnvironment, recovery.fullAccountId)
          .map { it.keysets.any { keyset -> keyset is PrivateMultisigRemoteKeyset } }
          .bind()

      // If the account already has a private keyset, we should keep them on a private keyset and
      // not accidentally migrate them back to a legacy keyset during a recovery.
      val shouldUseKeysetV2 = hasPrivateKeyset || (
        chaincodeDelegationFeatureFlag.isEnabled() &&
          updateToPrivateWalletOnRecoveryFeatureFlag.isEnabled()
      )

      val f8eSpendingKeyset = if (shouldUseKeysetV2) {
        createAccountKeysetV2F8eClient
          .createKeyset(
            f8eEnvironment = config.f8eEnvironment,
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            network = config.bitcoinNetworkType,
            appAuthKey = recovery.appGlobalAuthKey,
            hardwareProofOfPossession = hardwareProofOfPossession
          )
          .bind()
      } else {
        createAccountKeysetF8eClient
          .createKeyset(
            f8eEnvironment = config.f8eEnvironment,
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            network = config.bitcoinNetworkType,
            appAuthKey = recovery.appGlobalAuthKey,
            hardwareProofOfPossession = hardwareProofOfPossession
          )
          .bind()
      }

      recoveryStatusService.setLocalRecoveryProgress(
        LocalRecoveryAttemptProgress.CreatedSpendingKeys(
          f8eSpendingKeyset = f8eSpendingKeyset
        )
      ).bind()

      f8eSpendingKeyset
    }

  override suspend fun rotateAuthKeys(
    hardwareSignedChallenge: HardwareSignedChallenge,
    sealedCsek: SealedCsek,
    sealedSsek: SealedSsek,
  ): Result<Unit, Error> {
    return recoveryLock.withLock {
      coroutineBinding {
        val recovery = recoveryStatusService.status().first().bind()
        ensure(recovery is Recovery.StillRecovering) {
          Error("Recovery is not in progress")
        }

        // Hack for W-4377; this entire method needs to take at least 2 seconds, so the last step
        // is performed after this minimum delay because it triggers recompose via recovery change.
        withMinimumDelay(2.seconds) {
          ensure(hardwareSignedChallenge.signingFactor == Hardware) {
            Error("Expected $hardwareSignedChallenge to be signed with Hardware factor.")
          }

          recoveryStatusService
            .setLocalRecoveryProgress(
              LocalRecoveryAttemptProgress.AttemptingCompletion(
                sealedCsek = sealedCsek,
                sealedSsek = sealedSsek
              )
            ).bind()

          val destinationAppAuthPubKeys = AppAuthPublicKeys(
            recovery.appGlobalAuthKey,
            recovery.appRecoveryAuthKey,
            recovery.appGlobalAuthKeyHwSignature
          )

          val appSignedChallenge =
            appAuthKeyMessageSigner
              .signChallenge(
                publicKey = destinationAppAuthPubKeys.appGlobalAuthPublicKey,
                challenge = hardwareSignedChallenge.challenge
              )
              .mapError { Error(it) }
              .logFailure { "Error signing complete recovery challenge with app auth key." }
              .bind()

          val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
          completeDelayNotifyF8eClient
            .complete(
              f8eEnvironment = f8eEnvironment,
              fullAccountId = recovery.fullAccountId,
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

  override suspend fun rotateAuthTokens(): Result<Unit, Throwable> {
    return recoveryLock.withLock {
      coroutineBinding {
        val recovery = recoveryStatusService.status().first().bind()
        ensure(recovery is Recovery.StillRecovering) {
          Error("Recovery is not in progress")
        }

        val destinationAppAuthPubKeys = AppAuthPublicKeys(
          recovery.appGlobalAuthKey,
          recovery.appRecoveryAuthKey,
          recovery.appGlobalAuthKeyHwSignature
        )

        rotateAuthKeysAndTokens(
          accountId = recovery.fullAccountId,
          newAuthKeys = destinationAppAuthPubKeys
        ).bind()

        recoveryStatusService
          .setLocalRecoveryProgress(
            LocalRecoveryAttemptProgress.RotatedAuthKeys
          ).bind()
      }
    }
  }

  override suspend fun verifyAuthKeysAfterRotation(): Result<Unit, Error> =
    coroutineBinding {
      val recovery = recoveryStatusService.status().first().bind()
      ensure(recovery is Recovery.StillRecovering) {
        Error("Recovery is not in progress")
      }

      accountAuthenticator
        .appAuth(
          appAuthPublicKey = recovery.appGlobalAuthKey,
          authTokenScope = Global
        )
        .logAuthFailure { "Error authenticating with new app global auth key after recovery completed." }
        .bind()

      accountAuthenticator
        .appAuth(
          appAuthPublicKey = recovery.appRecoveryAuthKey,
          authTokenScope = RecoveryScope
        )
        .logAuthFailure { "Error authenticating with new app recovery auth key after recovery completed." }
        .bind()
    }

  override suspend fun regenerateTrustedContactCertificates(
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
  ): Result<Unit, Error> =
    coroutineBinding {
      val recovery = recoveryStatusService.status().first().bind()
      ensure(recovery is Recovery.StillRecovering) {
        Error("Recovery is not in progress")
      }

      // 1. Get latest trusted contacts from f8e
      val trustedContacts = relationshipsService
        .getRelationshipsWithoutSyncing(
          accountId = recovery.fullAccountId
        )
        .bind()
        .endorsedTrustedContacts

      // 2. Verify all trusted contacts with new auth keys
      endorseTrustedContactsService.authenticateRegenerateAndEndorse(
        accountId = recovery.fullAccountId,
        contacts = trustedContacts,
        oldAppGlobalAuthKey = oldAppGlobalAuthKey,
        oldHwAuthKey = recovery.hardwareAuthKey,
        newAppGlobalAuthKey = recovery.appGlobalAuthKey,
        newAppGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature
      ).bind()

      // 3. Re-sync relationships and store locally
      relationshipsService
        .syncAndVerifyRelationships(
          accountId = recovery.fullAccountId,
          appAuthKey = recovery.appGlobalAuthKey,
          hwAuthPublicKey = recovery.hardwareAuthKey
        )
        .bind()
    }

  override suspend fun removeTrustedContacts(): Result<Unit, Error> =
    coroutineBinding {
      val recovery = recoveryStatusService.status().first().bind()
      ensure(recovery is Recovery.StillRecovering) {
        Error("Recovery is not in progress")
      }

      val relationships = relationshipsService
        .getRelationshipsWithoutSyncing(
          accountId = recovery.fullAccountId
        )
        .logFailure { "Error fetching relationships for removal" }
        .bind()

      relationships.protectedCustomers.forEach { protectedCustomer ->
        relationshipsService.removeRelationshipWithoutSyncing(
          accountId = recovery.fullAccountId,
          hardwareProofOfPossession = null,
          RecoveryScope,
          protectedCustomer.relationshipId
        )
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
        authTokenScope = RecoveryScope
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
        .setTokens(accountId, recoveryAuthTokens, RecoveryScope)
        .mapError(::FailedToSaveAuthTokens)
        .bind()
    }
}
