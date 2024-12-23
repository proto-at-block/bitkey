package build.wallet.statemachine.recovery.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.UNSEAL_CLOUD_BACKUP
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED
import build.wallet.auth.*
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.SocRecV1BackupFeatures
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.backup.socRecDataAvailable
import build.wallet.cloud.backup.v2.FullAccountFields
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.cloud.backup.v2.SocRecV1AccountFeatures
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.logging.logFailure
import build.wallet.notifications.DeviceTokenManager
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.random.UuidGenerator
import build.wallet.recovery.RecoverySyncer
import build.wallet.recovery.socrec.PostSocRecTaskRepository
import build.wallet.recovery.socrec.SocRecChallengeRepository
import build.wallet.recovery.socrec.SocRecStartedChallengeDao
import build.wallet.recovery.socrec.toActions
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.cloud.CloudBackupRestorationUiState.*
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiProps
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiStateMachine
import build.wallet.toByteString
import build.wallet.toUByteList
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal const val START_SOCIAL_RECOVERY_MESSAGE = "Starting Recovery..."

// TODO(W-3756): migrate this logic to RecoveringAppFromCloudBackupDataStateMachineImpl.

@BitkeyInject(ActivityScope::class)
class FullAccountCloudBackupRestorationUiStateMachineImpl(
  private val accountAuthenticator: AccountAuthenticator,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
  private val authTokenDao: AuthTokenDao,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val backupRestorer: FullAccountCloudBackupRestorer,
  private val cloudBackupDao: CloudBackupDao,
  private val csekDao: CsekDao,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val deviceTokenManager: DeviceTokenManager,
  private val eventTracker: EventTracker,
  private val keyboxDao: KeyboxDao,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val recoveryChallengeStateMachine: RecoveryChallengeUiStateMachine,
  private val recoverySyncer: RecoverySyncer,
  private val socRecChallengeRepository: SocRecChallengeRepository,
  private val relationshipsService: RelationshipsService,
  private val postSocRecTaskRepository: PostSocRecTaskRepository,
  private val socRecStartedChallengeDao: SocRecStartedChallengeDao,
  private val uuidGenerator: UuidGenerator,
  private val fullAccountAuthKeyRotationService: FullAccountAuthKeyRotationService,
) : FullAccountCloudBackupRestorationUiStateMachine {
  @Composable
  override fun model(props: FullAccountCloudBackupRestorationUiProps): ScreenModel {
    var uiState: CloudBackupRestorationUiState by remember {
      mutableStateOf(CloudBackupFoundUiState)
    }

    // Reusable model for a loading screen while completing multiple restoration steps.
    val loadingRestoringFromBackupModel =
      LoadingBodyModel(
        message = "Restoring from backup...",
        onBack = { uiState = CloudBackupFoundUiState },
        id = CloudEventTrackerScreenId.LOADING_RESTORING_FROM_CLOUD_BACKUP
      ).asRootScreen()

    return when (val state = uiState) {
      is RecoveryAuthenticationState -> {
        RecoveryAuthenticationEffect(state, setState = { uiState = it })
        LoadingBodyModel(
          message = START_SOCIAL_RECOVERY_MESSAGE,
          onBack = { uiState = SocialRecoveryExplanationState },
          id = CloudEventTrackerScreenId.CLOUD_RECOVERY_AUTHENTICATION
        ).asRootScreen()
      }

      is SocRecRestorationState -> {
        SocRecRestoreEffect(props, state, setState = { uiState = it })
        loadingRestoringFromBackupModel
      }

      is SocRecChallengeState -> {
        recoveryChallengeStateMachine.model(
          RecoveryChallengeUiProps(
            accountId = state.accountId,
            f8eEnvironment = state.f8eEnvironment,
            actions =
              socRecChallengeRepository.toActions(
                state.accountId,
                state.f8eEnvironment,
                state.isUsingSocRecFakes
              ),
            endorsedTrustedContacts = state.contacts,
            relationshipIdToSocRecPkekMap =
              state.accountFeatures.socRecSealedDekMap
                .mapValues { it.value },
            sealedPrivateKeyMaterial = state.accountFeatures.socRecSealedFullAccountKeys,
            onExit = { uiState = CloudBackupFoundUiState },
            onKeyRecovered = {
              uiState =
                SocRecRestorationState(
                  accountId = state.accountId,
                  it
                )
            }
          )
        )
      }

      is CloudBackupFoundUiState ->
        CloudBackupFoundModel(
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
          onBack = props.onExit,
          onRestore = {
            uiState = UnsealingCsek
          },
          showSocRecButton = props.backup.socRecDataAvailable,
          onLostBitkeyClick = {
            uiState = SocialRecoveryExplanationState
          }
        ).asRootScreen()

      SocialRecoveryExplanationState -> {
        SocialRecoveryExplanationModel(
          onBack = {
            uiState = CloudBackupFoundUiState
          },
          onContinue = {
            val backup = props.backup as? CloudBackupV2
            val account = backup?.fullAccountFields as? SocRecV1AccountFeatures

            uiState =
              if (account == null) {
                RestoringFromBackupFailureUiState(
                  errorData = ErrorData(
                    segment = RecoverySegment.SocRec.ProtectedCustomer.Restoration,
                    actionDescription = "Reading full account from backup",
                    cause = Error("Backup did not contain data for full account")
                  ),
                  onBack = { uiState = SocialRecoveryExplanationState }
                )
              } else {
                RecoveryAuthenticationState(
                  f8eEnvironment = backup.f8eEnvironment,
                  accountFeatures = account,
                  backupFeatures = backup
                )
              }
          }
        ).asRootScreen()
      }

      is UnsealingCsek -> {
        val sealedCsek =
          when (props.backup) {
            is CloudBackupV2 -> (props.backup.fullAccountFields as FullAccountFields).sealedHwEncryptionKey
          }
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              Csek(
                SymmetricKeyImpl(
                  commands.unsealKey(session, sealedCsek.toUByteList()).toByteString()
                )
              )
            },
            onSuccess = { unsealedCsek ->
              csekDao.set(
                key = sealedCsek,
                value = unsealedCsek
              )
              uiState =
                RestoringFromBackupUiState
            },
            onCancel = { uiState = CloudBackupFoundUiState },
            isHardwareFake = props.debugOptions.isHardwareFake,
            screenPresentationStyle = Root,
            eventTrackerContext = UNSEAL_CLOUD_BACKUP,
            segment = RecoverySegment.CloudBackup.FullAccount.Restoration,
            actionDescription = "Unsealing CSEK for full account cloud restoration"
          )
        )
      }

      is RestoringFromBackupUiState -> {
        RestoringFromBackupEffect(props, setState = {
          uiState = it
        })
        loadingRestoringFromBackupModel
      }

      is CompletingCloudRecoveryUiState -> {
        CompleteCloudRecoveryEffect(props, state, setState = { uiState = it })
        loadingRestoringFromBackupModel
      }

      is RestoringFromBackupFailureUiState ->
        ErrorFormBodyModel(
          title = "We were unable to restore your wallet from a backup",
          primaryButton =
            ButtonDataModel(
              text = "Back",
              onClick = {
                uiState = SocialRecoveryExplanationState
              }
            ),
          eventTrackerScreenId = CloudEventTrackerScreenId.FAILURE_RESTORE_FROM_CLOUD_BACKUP,
          errorData = state.errorData
        ).asRootScreen()

      is SocRecRestorationFailedState ->
        ErrorFormBodyModel(
          title = "We were unable to complete your restoration",
          secondaryButton = ButtonDataModel(text = "Back", onClick = props.onExit),
          primaryButton =
            ButtonDataModel(
              text = "Retry",
              onClick = {
                uiState =
                  SocRecRestorationState(
                    accountId = state.accountId,
                    fullAccountKeys = state.fullAccountKeys
                  )
              }
            ),
          eventTrackerScreenId = CloudEventTrackerScreenId.FAILURE_RESTORE_FROM_CLOUD_BACKUP,
          errorData = ErrorData(
            segment = RecoverySegment.SocRec.ProtectedCustomer.Restoration,
            actionDescription = "Restoring full account from backup",
            cause = state.cause
          )
        ).asRootScreen()
    }
  }

  @Composable
  private fun RestoringFromBackupEffect(
    props: FullAccountCloudBackupRestorationUiProps,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ) {
    LaunchedEffect("restoring-from-backup") {
      backupRestorer
        .restoreFromBackup(cloudBackup = props.backup)
        .logFailure { "Error restoring keybox from cloud backup" }
        .onFailure {
          setState(
            RestoringFromBackupFailureUiState(
              errorData = ErrorData(
                segment = RecoverySegment.CloudBackup.FullAccount.Restoration,
                actionDescription = "Restoring full account from backup",
                cause = it
              ),
              onBack = props.onExit
            )
          )
        }
        .onSuccess { accountRestoration ->
          setState(CompletingCloudRecoveryUiState(accountRestoration))
        }
    }
  }

  @Composable
  private fun CompleteCloudRecoveryEffect(
    props: FullAccountCloudBackupRestorationUiProps,
    state: CompletingCloudRecoveryUiState,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ) {
    LaunchedEffect("completing-cloud-recovery") {
      handleCloudKeyRecovered(props, state.accountRestoration)
        .onFailure {
          setState(
            RestoringFromBackupFailureUiState(
              errorData = ErrorData(
                segment = RecoverySegment.CloudBackup.FullAccount.Restoration,
                actionDescription = "Authenticating with new app auth key and applying cloud backup",
                cause = it
              ),
              onBack = props.onExit
            )
          )
        }
        .onSuccess {
          fullAccountAuthKeyRotationService.recommendKeyRotation()
          keyboxDao
            .saveKeyboxAsActive(state.accountRestoration.asKeybox(uuidGenerator.random(), it))
            .onFailure { error ->
              setState(
                RestoringFromBackupFailureUiState(
                  errorData = ErrorData(
                    segment = RecoverySegment.CloudBackup.FullAccount.Restoration,
                    actionDescription = "Saving keybox as active",
                    cause = error
                  ),
                  onBack = props.onExit
                )
              )
            }
        }
    }
  }

  @Composable
  private fun SocRecRestoreEffect(
    props: FullAccountCloudBackupRestorationUiProps,
    state: SocRecRestorationState,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ) {
    LaunchedEffect("complete-socrec-restore") {
      coroutineBinding {
        val restoration =
          backupRestorer.restoreFromBackupWithDecryptedKeys(
            cloudBackup = props.backup,
            keysInfo = state.fullAccountKeys
          ).bind()

        // Set the flag to show the replace hardware card nudge
        // this flag is used by the MoneyHomeCardsUiStateMachine
        // and toggled off by the RecoveryInProgressDataStateMachine
        postSocRecTaskRepository.setHardwareReplacementNeeded(true).bind()

        // Remove the completed SocRec Challenge from the database
        socRecStartedChallengeDao.clear()

        // Put us into a state where we can start the hardware recovery flow
        val accountId = handleCloudKeyRecovered(props, restoration).bind()
        keyboxDao
          .saveKeyboxAsActive(
            restoration.asKeybox(
              keyboxId = uuidGenerator.random(),
              fullAccountId = accountId
            )
          )
          .bind()
      }.onFailure {
        setState(
          SocRecRestorationFailedState(
            accountId = state.accountId,
            fullAccountKeys = state.fullAccountKeys,
            cause = it
          )
        )
      }
    }
  }

  private suspend fun handleCloudKeyRecovered(
    props: FullAccountCloudBackupRestorationUiProps,
    accountRestoration: AccountRestoration,
  ): Result<FullAccountId, Error> =
    coroutineBinding {
      eventTracker.track(ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED)

      // Authenticate with f8e using recovered app [Global] authentication key.
      val globalAuthData =
        authenticateWithF8eAndStoreAuthTokens(
          f8eEnvironment = props.debugOptions.f8eEnvironment,
          appAuthPublicKey = accountRestoration.activeAppKeyBundle.authKey,
          tokenScope = AuthTokenScope.Global
        ).bind()
      val accountId = FullAccountId(globalAuthData.accountId)

      // Authenticate with f8e using recovered app [Recovery] authentication key.
      authenticateWithF8eAndStoreAuthTokens(
        f8eEnvironment = props.debugOptions.f8eEnvironment,
        appAuthPublicKey = accountRestoration.activeAppKeyBundle.recoveryAuthKey,
        tokenScope = AuthTokenScope.Recovery
      ).bind()

      // TODO(W-1535): this should be prompted by a notification prompt
      deviceTokenManager
        .addDeviceTokenIfPresentForAccount(
          fullAccountId = accountId,
          f8eEnvironment = props.debugOptions.f8eEnvironment,
          authTokenScope = AuthTokenScope.Global
        )

      cloudBackupDao
        .set(accountId.serverId, accountRestoration.cloudBackupForLocalStorage)
        .bind()

      // Clear out ongoing Lost Hardware DN recovery, if any.
      recoverySyncer
        .clear()
        .bind()

      // Attempt to sync social relationships before completing the recovery to ensure that
      // the background refresh doesn't delete existing TCs. But don't bind any failures.
      relationshipsService.syncAndVerifyRelationships(
        accountId = accountId,
        f8eEnvironment = props.debugOptions.f8eEnvironment,
        appAuthKey = accountRestoration.activeAppKeyBundle.authKey,
        hwAuthPublicKey = accountRestoration.activeHwKeyBundle.authKey
      )

      // Attempt to sync the new wallet before completing the recovery and showing
      // Money Home (saving the keybox as active will complete and update UI), but
      // don't bind any failures.
      appSpendingWalletProvider.getSpendingWallet(accountRestoration.activeSpendingKeyset)
        .onSuccess { it.sync() }

      accountId
    }

  /**
   * Performs auth with f8e using the given [AppAuthPublicKey] and stores the resulting
   * tokens in [AuthTokenDao] keyed by the given [AuthTokenScope]
   */
  private suspend fun authenticateWithF8eAndStoreAuthTokens(
    appAuthPublicKey: PublicKey<out AppAuthKey>,
    f8eEnvironment: F8eEnvironment,
    tokenScope: AuthTokenScope,
  ): Result<AccountAuthenticator.AuthData, Error> {
    return coroutineBinding {
      val authData =
        accountAuthenticator
          .appAuth(
            f8eEnvironment = f8eEnvironment,
            appAuthPublicKey = appAuthPublicKey,
            authTokenScope = tokenScope
          )
          .logAuthFailure { "Error authenticating with new app auth key after recovery completed." }
          .bind()

      val fullAccountId = FullAccountId(authData.accountId)
      authTokenDao
        .setTokensOfScope(fullAccountId, authData.authTokens, tokenScope)
        .mapError { Error(it) }
        .bind()

      authData
    }
  }

  @Composable
  private fun RecoveryAuthenticationEffect(
    state: RecoveryAuthenticationState,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ) {
    LaunchedEffect("lost-bitkey-auth") {
      appPrivateKeyDao.storeAppKeyPair(state.backupFeatures.appRecoveryAuthKeypair)
        .onFailure {
          setState(
            RestoringFromBackupFailureUiState(
              errorData = ErrorData(
                segment = RecoverySegment.SocRec.ProtectedCustomer.Restoration,
                actionDescription = "Storing app recovery auth key",
                cause = it
              ),
              onBack = { setState(SocialRecoveryExplanationState) }
            )
          )
          return@LaunchedEffect
        }

      authenticateWithF8eAndStoreAuthTokens(
        f8eEnvironment = state.f8eEnvironment,
        appAuthPublicKey = state.backupFeatures.appRecoveryAuthKeypair.publicKey,
        tokenScope = AuthTokenScope.Recovery
      ).flatMap { authData ->
        relationshipsService
          .getRelationshipsWithoutSyncing(
            accountId = FullAccountId(authData.accountId),
            f8eEnvironment = state.f8eEnvironment
          )
          .map {
            Pair(authData, it)
          }
      }.onSuccess { (authData, relationships) ->
        setState(
          SocRecChallengeState(
            accountId = FullAccountId(authData.accountId),
            f8eEnvironment = state.f8eEnvironment,
            contacts = relationships.endorsedTrustedContacts.toImmutableList(),
            isUsingSocRecFakes = state.backupFeatures.isUsingSocRecFakes,
            accountFeatures = state.accountFeatures,
            backupFeatures = state.backupFeatures
          )
        )
      }.onFailure {
        setState(
          RestoringFromBackupFailureUiState(
            errorData = ErrorData(
              segment = RecoverySegment.SocRec.ProtectedCustomer.Restoration,
              actionDescription = "Authenticating with new app recovery auth key, storing tokens, and syncing socrec relationships",
              cause = it
            ),
            onBack = { setState(SocialRecoveryExplanationState) }
          )
        )
      }
    }
  }
}

private sealed interface CloudBackupRestorationUiState {
  /**
   * Initial state – found wallet backup on the cloud storage. Confirm with user they want to restore.
   */
  data object CloudBackupFoundUiState : CloudBackupRestorationUiState

  /**
   * Customer has chosen to restore. Show NFC prompt to unseal the CSEK.
   */
  data object UnsealingCsek : CloudBackupRestorationUiState

  /**
   * Restoring the account from the backup using the CSEK.
   * We also track the hw authentication key since it's needed
   * if the customer wishes to rotate the authentication keys after the
   * cloud backup restoration.
   */
  data object RestoringFromBackupUiState : CloudBackupRestorationUiState

  /**
   * Failure when restoring the account from the backup.
   */
  data class RestoringFromBackupFailureUiState(
    val errorData: ErrorData,
    val onBack: () -> Unit,
  ) : CloudBackupRestorationUiState

  /**
   * Used at the end of the Cloud restoration flow
   * to rotate the auth keys so the device is now the active device
   */
  data class CompletingCloudRecoveryUiState(
    val accountRestoration: AccountRestoration,
  ) : CloudBackupRestorationUiState

  data object SocialRecoveryExplanationState : CloudBackupRestorationUiState

  /**
   * Uses the recovery key to authenticate and restore an account from
   * a cloud backup.
   */
  data class RecoveryAuthenticationState(
    val f8eEnvironment: F8eEnvironment,
    val accountFeatures: SocRecV1AccountFeatures,
    val backupFeatures: SocRecV1BackupFeatures,
  ) : CloudBackupRestorationUiState

  /**
   * Starts the Social Recovery challenge flow for restoring
   * using trusted contacts instead of hardware.
   */
  data class SocRecChallengeState(
    val accountId: FullAccountId,
    val f8eEnvironment: F8eEnvironment,
    val contacts: ImmutableList<EndorsedTrustedContact>,
    val isUsingSocRecFakes: Boolean,
    val accountFeatures: SocRecV1AccountFeatures,
    val backupFeatures: SocRecV1BackupFeatures,
  ) : CloudBackupRestorationUiState

  /**
   * Starts restoring account data after a successful social recovery.
   */
  data class SocRecRestorationState(
    val accountId: FullAccountId,
    val fullAccountKeys: FullAccountKeys,
  ) : CloudBackupRestorationUiState

  /**
   * The restoration process after a social recovery challenge failed
   * to complete.
   */
  data class SocRecRestorationFailedState(
    val accountId: FullAccountId,
    val fullAccountKeys: FullAccountKeys,
    val cause: Error,
  ) : CloudBackupRestorationUiState
}
