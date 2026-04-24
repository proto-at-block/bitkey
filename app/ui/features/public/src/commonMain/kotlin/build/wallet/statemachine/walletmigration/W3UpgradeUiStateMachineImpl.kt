package build.wallet.statemachine.walletmigration

import androidx.compose.runtime.*
import bitkey.account.HardwareType
import bitkey.auth.AuthTokenScope.Global
import bitkey.privilegedactions.ActionProofService
import bitkey.privilegedactions.ActionProofService.Companion.ACTION_PROOF_VERSION
import bitkey.privilegedactions.AppSignedActionProof
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.auth.AuthTokensService
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.extractAccountIndex
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.getTransactionData
import build.wallet.bitcoin.utxo.UtxoConsolidationContext
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.catchingResult
import build.wallet.chaincode.delegation.ChaincodeExtractor
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.Sek
import build.wallet.cloud.backup.csek.SsekDao
import build.wallet.cloud.backup.health.AppKeyBackupStatus
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.crypto.WsmVerifier
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.ensureNotNull
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.auth.PrivilegedActionProof.HwSignedAction
import build.wallet.feature.flags.UtxoMaxConsolidationCountFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import build.wallet.nfc.NfcException
import build.wallet.nfc.platform.*
import build.wallet.nfc.transaction.PairingTransactionResponse
import build.wallet.recovery.sweep.SweepContext
import build.wallet.relationships.RelationshipsKeysRepository
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.account.create.full.hardware.PairingContext
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.cloud.health.RepairAppKeyBackupProps
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.nfc.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import build.wallet.statemachine.utxo.UtxoConsolidationProps
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachine
import build.wallet.statemachine.walletmigration.W3UpgradeUiState.ShowingCloudBackupUnhealthyWarning
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.wallet.migration.MigrationError
import build.wallet.wallet.migration.MigrationProgress
import build.wallet.wallet.migration.MigrationService
import build.wallet.wallet.migration.MigrationType
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import uniffi.actionproof.Action
import build.wallet.analytics.v1.Action as AnalyticsAction

@BitkeyInject(ActivityScope::class)
@Suppress("LargeClass")
class W3UpgradeUiStateMachineImpl(
  private val pairNewHardwareUiStateMachine: PairNewHardwareUiStateMachine,
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val sweepUiStateMachine: SweepUiStateMachine,
  private val migrationService: MigrationService,
  private val fullAccountCloudSignInAndBackupUiStateMachine:
    FullAccountCloudSignInAndBackupUiStateMachine,
  private val ssekDao: SsekDao,
  private val accountService: AccountService,
  private val authTokensService: AuthTokensService,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val nfcConfirmableSessionUiStateMachine: NfcConfirmableSessionUiStateMachine,
  private val actionProofService: ActionProofService,
  private val relationshipsKeysRepository: RelationshipsKeysRepository,
  private val appKeysGenerator: AppKeysGenerator,
  private val chaincodeExtractor: ChaincodeExtractor,
  private val bitcoinWalletService: BitcoinWalletService,
  private val wsmVerifier: WsmVerifier,
  private val utxoConsolidationUiStateMachine: UtxoConsolidationUiStateMachine,
  private val utxoMaxConsolidationCountFeatureFlag: UtxoMaxConsolidationCountFeatureFlag,
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val repairCloudBackupStateMachine: RepairCloudBackupStateMachine,
  private val eventTracker: EventTracker,
) : W3UpgradeUiStateMachine {
  @Composable
  override fun model(props: W3UpgradeUiProps): ScreenModel {
    var uiState by remember {
      mutableStateOf<W3UpgradeUiState>(W3UpgradeUiState.Loading)
    }
    val scope = rememberStableCoroutineScope()

    // Check if migration is in progress by calling resume(), then navigate
    // to the correct screen. Avoids flashing the intro before bouncing.
    var isMigrationInProgress by remember { mutableStateOf(false) }
    var resumedFromCloudBackupFlow by remember { mutableStateOf(false) }
    LaunchedEffect("check-w3-upgrade-status") {
      val resolved = resolveInitialUiState()
      isMigrationInProgress = resolved.isMigrationInProgress
      resumedFromCloudBackupFlow = resolved.resumedFromCloudBackup
      uiState = resolved.uiState
    }

    return when (val state = uiState) {
      is W3UpgradeUiState.Loading,
      is W3UpgradeUiState.ShowingIntro,
      is W3UpgradeUiState.ShowingDeviceReady,
      is ShowingCloudBackupUnhealthyWarning,
      is W3UpgradeUiState.RepairingCloudBackup,
      is W3UpgradeUiState.CheckingPendingTransactions,
      is W3UpgradeUiState.ShowingPendingTransactionsWarning,
      is W3UpgradeUiState.ShowingUtxoConsolidationRequired,
      is W3UpgradeUiState.UtxoConsolidation,
      ->
        IntroPhaseModel(
          state = uiState,
          props = props,
          scope = scope,
          isMigrationInProgress = isMigrationInProgress,
          resumedFromCloudBackupFlow = resumedFromCloudBackupFlow,
          onStateChange = { uiState = it },
          firmwareDeviceInfoDao = firmwareDeviceInfoDao,
          bitcoinWalletService = bitcoinWalletService,
          utxoConsolidationUiStateMachine = utxoConsolidationUiStateMachine,
          utxoMaxConsolidationCountFeatureFlag = utxoMaxConsolidationCountFeatureFlag,
          cloudBackupHealthRepository = cloudBackupHealthRepository,
          repairCloudBackupStateMachine = repairCloudBackupStateMachine,
          eventTracker = eventTracker
        )

      is W3UpgradeUiState.PairingNewHardware,
      ->
        PairingPhaseModel(
          state = uiState,
          props = props,
          resumedFromCloudBackupFlow = resumedFromCloudBackupFlow,
          onStateChange = { uiState = it }
        )

      is W3UpgradeUiState.GeneratingAuthKeys,
      is W3UpgradeUiState.ShowingOldHardwareInstructionsForAuthorization,
      is W3UpgradeUiState.TappingOldHardwareForAuthorization,
      is W3UpgradeUiState.CreatingKeyset,
      is W3UpgradeUiState.AttemptingResumedAuthKeyRotation,
      is W3UpgradeUiState.PreparingNewHardwareRotation,
      is W3UpgradeUiState.ShowingNewHardwareInstructionsForRotation,
      is W3UpgradeUiState.TappingNewHardwareForRotation,
      is W3UpgradeUiState.RunningAuthRotation,
      is W3UpgradeUiState.PreparingUpgradeAuthorization,
      is W3UpgradeUiState.AuthorizingW3Upgrade,
      is W3UpgradeUiState.RunningServerKeysetActivation,
      is W3UpgradeUiState.ProvisioningHardwareDescriptor,
      is W3UpgradeUiState.ShowingWrongHardwareError,
      ->
        AuthRotationPhaseModel(
          state = uiState,
          props = props,
          resumedFromCloudBackupFlow = resumedFromCloudBackupFlow,
          onStateChange = { uiState = it },
          onMigrationStarted = { isMigrationInProgress = true },
          onRequestExit = {
            uiState = W3UpgradeUiState.ConfirmingExit(previousState = uiState)
          }
        )

      is W3UpgradeUiState.CloudBackup,
      is W3UpgradeUiState.CheckingForFunds,
      is W3UpgradeUiState.ShowingOldHardwareInstructions,
      is W3UpgradeUiState.Sweeping,
      ->
        BackupAndSweepPhaseModel(
          state = uiState,
          props = props,
          scope = scope,
          resumedFromCloudBackupFlow = resumedFromCloudBackupFlow,
          onStateChange = { uiState = it }
        )

      is W3UpgradeUiState.Success,
      is W3UpgradeUiState.Error,
      ->
        TerminalPhaseModel(
          state = uiState,
          props = props,
          scope = scope,
          isMigrationInProgress = isMigrationInProgress,
          onStateChange = { uiState = it },
          onMigrationInProgress = { isMigrationInProgress = it },
          onResumedFromCloudBackupChange = { resumedFromCloudBackupFlow = it },
          accountService = accountService,
          resolveInitialUiState = ::resolveInitialUiState,
          eventTracker = eventTracker
        )

      is W3UpgradeUiState.ConfirmingExit -> {
        ScreenModel(
          body = W3UpgradeOldHardwareAuthRotationInstructionsBodyModel(
            onBack = null,
            onContinue = {},
            onDeferExit = {}
          ),
          alertModel = w3UpgradeExitConfirmationAlertModel(
            onConfirm = {
              eventTracker.track(AnalyticsAction.ACTION_APP_W3_UPGRADE_CANCELLED)
              props.onExit()
            },
            onDismiss = { uiState = state.previousState }
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }
    }
  }

  @Composable
  private fun PairingPhaseModel(
    state: W3UpgradeUiState,
    props: W3UpgradeUiProps,
    resumedFromCloudBackupFlow: Boolean,
    onStateChange: (W3UpgradeUiState) -> Unit,
  ): ScreenModel =
    when (state) {
      is W3UpgradeUiState.PairingNewHardware -> {
        pairNewHardwareUiStateMachine.model(
          PairNewHardwareProps(
            request = PairNewHardwareProps.Request.Ready(
              appGlobalAuthPublicKey = props.account.keybox.activeAppKeyBundle.authKey,
              onSuccess = { fingerprintEnrolled ->
                eventTracker.track(AnalyticsAction.ACTION_APP_W3_UPGRADE_HW_PAIRED)
                if (resumedFromCloudBackupFlow) {
                  onStateChange(
                    W3UpgradeUiState.GeneratingAuthKeys(
                      fingerprintEnrolled = fingerprintEnrolled,
                      oldDeviceSerial = state.oldDeviceSerial,
                      oldHardwareFingerprint = state.oldHardwareFingerprint
                    )
                  )
                } else {
                  // Show old hardware instructions immediately (keyset creation deferred until W1 tap)
                  onStateChange(
                    W3UpgradeUiState.ShowingOldHardwareInstructionsForAuthorization(
                      fingerprintEnrolled = fingerprintEnrolled,
                      newAppGlobalAuthKey = null,
                      newAppRecoveryAuthKey = null,
                      oldDeviceSerial = state.oldDeviceSerial,
                      oldHardwareFingerprint = state.oldHardwareFingerprint
                    )
                  )
                }
              }
            ),
            onExit = {
              onStateChange(W3UpgradeUiState.ShowingIntro)
            },
            eventTrackerContext = build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext.PAIR_NEW_DEVICE_DURING_W3_UPGRADE,
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            pairingContext = PairingContext.W3Upgrade
          )
        )
      }
      else -> error("Unexpected state in PairingPhaseModel: $state")
    }

  @Composable
  private fun AuthRotationPhaseModel(
    state: W3UpgradeUiState,
    props: W3UpgradeUiProps,
    resumedFromCloudBackupFlow: Boolean,
    onStateChange: (W3UpgradeUiState) -> Unit,
    onMigrationStarted: () -> Unit,
    onRequestExit: () -> Unit,
  ): ScreenModel =
    when (state) {
      is W3UpgradeUiState.GeneratingAuthKeys -> {
        LaunchedEffect(Unit) {
          val persistedAuthKeys = state.resumedAuthRotation?.newAppAuthKeys
          val generatedRecoveryAuthKey = if (persistedAuthKeys == null) {
            appKeysGenerator.generateRecoveryAuthKey().get()
          } else {
            null
          }
          if (persistedAuthKeys == null && generatedRecoveryAuthKey == null) {
            onStateChange(W3UpgradeUiState.Error)
            return@LaunchedEffect
          }
          val (appGlobalAuthKey, appRecoveryAuthKey) = authKeysForRotation(
            account = props.account,
            resumedAuthRotation = state.resumedAuthRotation,
            generatedRecoveryAuthKey = generatedRecoveryAuthKey
          )
          val resumedFromCloudBackup = resumedFromCloudBackupFlow ||
            state.resumedAuthRotation?.resumedFromCloudBackup == true
          when {
            resumedFromCloudBackup && state.fingerprintEnrolled != null -> {
              onStateChange(
                W3UpgradeUiState.CreatingKeyset(
                  fingerprintEnrolled = state.fingerprintEnrolled,
                  newAppGlobalAuthKey = appGlobalAuthKey,
                  newAppRecoveryAuthKey = appRecoveryAuthKey,
                  w1ProofOfPossession = null,
                  oldDeviceSerial = state.oldDeviceSerial!!,
                  oldHardwareFingerprint = state.oldHardwareFingerprint!!
                )
              )
            }

            resumedFromCloudBackup && state.resumedAuthRotation != null -> {
              val updatedState = state.resumedAuthRotation.withAppAuthKeys(
                AppAuthPublicKeys(
                  appGlobalAuthPublicKey = appGlobalAuthKey,
                  appRecoveryAuthPublicKey = appRecoveryAuthKey,
                  appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
                    value = AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
                  )
                )
              )
              onStateChange(
                W3UpgradeUiState.PreparingNewHardwareRotation(
                  migrationProgress = updatedState,
                  newAppGlobalAuthKey = appGlobalAuthKey,
                  newAppRecoveryAuthKey = appRecoveryAuthKey
                )
              )
            }

            else -> {
              onStateChange(
                W3UpgradeUiState.TappingOldHardwareForAuthorization(
                  fingerprintEnrolled = state.fingerprintEnrolled,
                  newAppGlobalAuthKey = appGlobalAuthKey,
                  newAppRecoveryAuthKey = appRecoveryAuthKey,
                  resumedAuthRotation = state.resumedAuthRotation,
                  oldDeviceSerial = state.oldDeviceSerial,
                  oldHardwareFingerprint = state.oldHardwareFingerprint
                )
              )
            }
          }
        }

        w3LoadingScreenModel(
          "Preparing key rotation...",
          WalletMigrationEventTrackerScreenId.W3_UPGRADE_GENERATING_AUTH_KEYS
        )
      }
      is W3UpgradeUiState.ShowingOldHardwareInstructionsForAuthorization -> {
        ScreenModel(
          body = W3UpgradeOldHardwareAuthRotationInstructionsBodyModel(
            onBack = null,
            onContinue = {
              val resumedAuthRotation = state.resumedAuthRotation
              if (resumedAuthRotation?.canRetryWithOldHardwareProofOnly() == true) {
                val authKeys = checkNotNull(resumedAuthRotation.newAppAuthKeys)
                onStateChange(
                  W3UpgradeUiState.TappingOldHardwareForAuthorization(
                    newAppGlobalAuthKey = authKeys.appGlobalAuthPublicKey,
                    newAppRecoveryAuthKey = authKeys.appRecoveryAuthPublicKey,
                    resumedAuthRotation = resumedAuthRotation
                  )
                )
              } else {
                onStateChange(
                  W3UpgradeUiState.GeneratingAuthKeys(
                    fingerprintEnrolled = state.fingerprintEnrolled,
                    resumedAuthRotation = resumedAuthRotation,
                    oldDeviceSerial = state.oldDeviceSerial,
                    oldHardwareFingerprint = state.oldHardwareFingerprint
                  )
                )
              }
            },
            // Exit only available in pre-keyset flow (fingerprintEnrolled set, no migration yet)
            onDeferExit = state.fingerprintEnrolled?.let { onRequestExit }
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }
      is W3UpgradeUiState.TappingOldHardwareForAuthorization -> {
        // Tap the old W1 hardware to authorize rotating auth to the new W3.
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            request = Request.HwKeyProof(
              onSuccess = { hwFactorProofOfPossession ->
                if (state.fingerprintEnrolled != null) {
                  // First-time flow: keyset not yet created. Go create it now.
                  onStateChange(
                    W3UpgradeUiState.CreatingKeyset(
                      fingerprintEnrolled = state.fingerprintEnrolled,
                      newAppGlobalAuthKey = state.newAppGlobalAuthKey,
                      newAppRecoveryAuthKey = state.newAppRecoveryAuthKey,
                      w1ProofOfPossession = hwFactorProofOfPossession,
                      oldDeviceSerial = state.oldDeviceSerial!!,
                      oldHardwareFingerprint = state.oldHardwareFingerprint!!
                    )
                  )
                } else if (state.resumedAuthRotation != null) {
                  val existingState = state.resumedAuthRotation
                  val newAppAuthKeys = existingState.newAppAuthKeys ?: AppAuthPublicKeys(
                    appGlobalAuthPublicKey = state.newAppGlobalAuthKey,
                    appRecoveryAuthPublicKey = state.newAppRecoveryAuthKey,
                    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
                      value = AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
                    )
                  )
                  val updatedState = existingState.withProof(
                    newAppAuthKeys = newAppAuthKeys,
                    proof = PrivilegedActionProof.HwKeyProof(hwFactorProofOfPossession)
                  )
                  if (existingState.canRetryWithOldHardwareProofOnly()) {
                    onStateChange(
                      W3UpgradeUiState.RunningAuthRotation(
                        migrationProgress = updatedState
                      )
                    )
                  } else {
                    onStateChange(
                      W3UpgradeUiState.PreparingNewHardwareRotation(
                        migrationProgress = updatedState,
                        newAppGlobalAuthKey = state.newAppGlobalAuthKey,
                        newAppRecoveryAuthKey = state.newAppRecoveryAuthKey
                      )
                    )
                  }
                } else {
                  logError {
                    "TappingOldHardwareForAuthorization: no fingerprintEnrolled or resumedAuthRotation"
                  }
                  onStateChange(W3UpgradeUiState.Error)
                }
              }
            ),
            fullAccountId = props.account.accountId,
            appAuthKey = props.account.keybox.activeAppKeyBundle.authKey,
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            // Skip pairing check — user taps the OLD W1, not the paired W3.
            hardwareVerification = HardwareVerification.NotRequired,
            onBack = {
              // Return to instructions so the user can retry the tap.
              // Pre-keyset flow carries fingerprintEnrolled; resume flow has null fields.
              onStateChange(
                W3UpgradeUiState.ShowingOldHardwareInstructionsForAuthorization(
                  fingerprintEnrolled = state.fingerprintEnrolled,
                  newAppGlobalAuthKey = state.newAppGlobalAuthKey,
                  newAppRecoveryAuthKey = state.newAppRecoveryAuthKey,
                  resumedAuthRotation = state.resumedAuthRotation,
                  oldDeviceSerial = state.oldDeviceSerial,
                  oldHardwareFingerprint = state.oldHardwareFingerprint
                )
              )
            },
            // Enforce that the old W1 device is tapped, not the new W3.
            requiredHardwareType = HardwareType.W1,
            onError = wrongHardwareErrorHandler(
              expectedType = HardwareType.W1,
              retryState = state,
              onStateChange = onStateChange
            )
          )
        )
      }
      is W3UpgradeUiState.CreatingKeyset -> {
        LaunchedEffect(Unit) {
          // Retrieve the unsealed SSEK that was saved during hardware pairing
          val unsealedSsek = ssekDao.get(state.fingerprintEnrolled.sealedSsek)
            .get()
          if (unsealedSsek == null) {
            onStateChange(W3UpgradeUiState.Error)
            return@LaunchedEffect
          }

          val initialState = MigrationProgress.CreateNewKeyset.W3Upgrade(
            oldDeviceSerial = state.oldDeviceSerial,
            oldHardwareFingerprint = state.oldHardwareFingerprint,
            newDeviceSerial = state.fingerprintEnrolled.serial,
            currentKeybox = props.account.keybox,
            newHwSpendingKey = state.fingerprintEnrolled.keyBundle.spendingKey,
            hwProofOfPossession = HwFactorProofOfPossession(""),
            ssek = unsealedSsek,
            sealedSsek = state.fingerprintEnrolled.sealedSsek,
            sealedCsek = state.fingerprintEnrolled.sealedCsek,
            resumedFromCloudBackup = resumedFromCloudBackupFlow
          )

          // Loop through migration states until we reach one that needs UI interaction
          var currentState: MigrationProgress = initialState
          while (!currentState.requiresUiInteraction()) {
            migrationService.proceed(currentState)
              .onFailure {
                onStateChange(W3UpgradeUiState.Error)
                return@LaunchedEffect
              }
              .onSuccess { nextState ->
                currentState = nextState
              }
          }

          // Keyset creation succeeded — this is the point of no return.
          onMigrationStarted()

          // Attach the W1 proof and auth keys collected before keyset creation.
          val authRotation = currentState as? MigrationProgress.AuthKeyRotation
          if (authRotation != null) {
            val newAppAuthKeys = AppAuthPublicKeys(
              appGlobalAuthPublicKey = state.newAppGlobalAuthKey,
              appRecoveryAuthPublicKey = state.newAppRecoveryAuthKey,
              appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
                value = AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
              )
            )
            val updatedState = state.w1ProofOfPossession?.let { proof ->
              authRotation.withProof(
                newAppAuthKeys = newAppAuthKeys,
                proof = PrivilegedActionProof.HwKeyProof(proof)
              )
            } ?: authRotation.withAppAuthKeys(newAppAuthKeys)
            onStateChange(
              W3UpgradeUiState.PreparingNewHardwareRotation(
                migrationProgress = updatedState,
                newAppGlobalAuthKey = state.newAppGlobalAuthKey,
                newAppRecoveryAuthKey = state.newAppRecoveryAuthKey
              )
            )
          } else {
            onStateChange(
              uiStateForMigrationProgress(currentState)
                ?: W3UpgradeUiState.Error
            )
          }
        }

        w3LoadingScreenModel(
          "Setting up your new device...",
          WalletMigrationEventTrackerScreenId.W3_UPGRADE_CREATING_KEYSET
        )
      }
      is W3UpgradeUiState.AttemptingResumedAuthKeyRotation -> {
        LaunchedEffect(Unit) {
          migrationService.proceed(state.migrationProgress)
            .onSuccess { nextState ->
              eventTracker.track(AnalyticsAction.ACTION_APP_W3_UPGRADE_AUTH_ROTATED)
              onStateChange(
                uiStateForMigrationProgress(nextState)
                  ?: W3UpgradeUiState.Error
              )
            }
            .onFailure { error ->
              when {
                error is MigrationError.MissingContext.W3AuthRotationOldHardwareProof -> {
                  onStateChange(
                    W3UpgradeUiState.ShowingOldHardwareInstructionsForAuthorization(
                      resumedAuthRotation = state.migrationProgress,
                      newAppGlobalAuthKey = state.migrationProgress.newAppAuthKeys
                        ?.appGlobalAuthPublicKey,
                      newAppRecoveryAuthKey = state.migrationProgress.newAppAuthKeys
                        ?.appRecoveryAuthPublicKey
                    )
                  )
                }

                error is MigrationError.MissingContext.W3AuthRotationNewHardwareActionProof -> {
                  val authKeys = state.migrationProgress.newAppAuthKeys
                  if (authKeys == null) {
                    onStateChange(
                      W3UpgradeUiState.GeneratingAuthKeys(
                        resumedAuthRotation = state.migrationProgress
                      )
                    )
                  } else {
                    onStateChange(
                      W3UpgradeUiState.PreparingNewHardwareRotation(
                        migrationProgress = state.migrationProgress,
                        newAppGlobalAuthKey = authKeys.appGlobalAuthPublicKey,
                        newAppRecoveryAuthKey = authKeys.appRecoveryAuthPublicKey
                      )
                    )
                  }
                }

                else -> {
                  logError { "Failed resumed auth rotation attempt: $error" }
                  onStateChange(W3UpgradeUiState.Error)
                }
              }
            }
        }

        w3LoadingScreenModel(
          "Resuming upgrade...",
          WalletMigrationEventTrackerScreenId.W3_UPGRADE_RESUMING_AUTH_KEY_ROTATION
        )
      }
      is W3UpgradeUiState.PreparingNewHardwareRotation -> {
        LaunchedEffect(Unit) {
          if (state.migrationProgress.resumedFromCloudBackup && state.rotateAppAuthKeysSigned == null) {
            ensureFreshGlobalTokensForActionProof(props.account)
              .onSuccess {
                actionProofService.buildAppSignedPayload(
                  action = Action.ROTATE_APP_AUTH_KEYS,
                  appAuthKey = state.migrationProgress.currentKeybox.activeAppKeyBundle.authKey,
                  accountId = props.account.accountId
                )
                  .onSuccess { rotateAppAuthKeysSigned ->
                    onStateChange(
                      W3UpgradeUiState.ShowingNewHardwareInstructionsForRotation(
                        migrationProgress = state.migrationProgress,
                        newAppGlobalAuthKey = state.newAppGlobalAuthKey,
                        newAppRecoveryAuthKey = state.newAppRecoveryAuthKey,
                        rotateAppAuthKeysSigned = rotateAppAuthKeysSigned
                      )
                    )
                  }
                  .onFailure {
                    onStateChange(W3UpgradeUiState.Error)
                  }
              }
              .onFailure {
                onStateChange(W3UpgradeUiState.Error)
              }
          } else {
            onStateChange(
              W3UpgradeUiState.ShowingNewHardwareInstructionsForRotation(
                migrationProgress = state.migrationProgress,
                newAppGlobalAuthKey = state.newAppGlobalAuthKey,
                newAppRecoveryAuthKey = state.newAppRecoveryAuthKey,
                rotateAppAuthKeysSigned = state.rotateAppAuthKeysSigned
              )
            )
          }
        }

        w3LoadingScreenModel(
          "Preparing auth rotation...",
          WalletMigrationEventTrackerScreenId.W3_UPGRADE_PREPARING_AUTH_ROTATION
        )
      }
      is W3UpgradeUiState.ShowingNewHardwareInstructionsForRotation -> {
        ScreenModel(
          body = W3UpgradeNewHardwareAuthRotationInstructionsBodyModel(
            onBack = null,
            step = if (resumedFromCloudBackupFlow) 2 else 3,
            totalSteps = if (resumedFromCloudBackupFlow) 3 else 4,
            onContinue = {
              onStateChange(
                W3UpgradeUiState.TappingNewHardwareForRotation(
                  migrationProgress = state.migrationProgress,
                  newAppGlobalAuthKey = state.newAppGlobalAuthKey,
                  newAppRecoveryAuthKey = state.newAppRecoveryAuthKey,
                  rotateAppAuthKeysSigned = state.rotateAppAuthKeysSigned
                )
              )
            }
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }
      is W3UpgradeUiState.TappingNewHardwareForRotation -> {
        // W3 composite NFC tap: signs app global auth key and account ID
        var w3DeviceInfo by remember { mutableStateOf<FirmwareDeviceInfo?>(null) }
        if (state.migrationProgress.resumedFromCloudBackup) {
          val rotateAppAuthKeysSigned = checkNotNull(state.rotateAppAuthKeysSigned)
          nfcConfirmableSessionUiStateMachine.model(
            NfcConfirmableSessionUIStateMachineProps(
              session = { session, commands ->
                commands.verifyHardwareType(session, expectedType = HardwareType.W3)
                w3DeviceInfo = commands.getDeviceInfo(session)
                commands.rotateAppAuthKeys(
                  session = session,
                  params = RotateAppAuthKeysContinueParams(
                    actionProofVersion = ACTION_PROOF_VERSION,
                    actionProofAction = ActionProofAction.ROTATE_APP_AUTH_KEYS,
                    actionProofBindings = rotateAppAuthKeysSigned.bindings,
                    accountId = props.account.accountId.serverId,
                    appGlobalAuthPublicKey = state.newAppGlobalAuthKey.value
                  )
                )
              },
              onSuccess = { result ->
                coroutineBinding {
                  w3DeviceInfo?.let { firmwareDeviceInfoDao.setDeviceInfo(it).bind() }
                  val header = actionProofService.createActionProofHeader(
                    signatures = listOf(
                      rotateAppAuthKeysSigned.appSignature,
                      result.actionProofSignature.lowercase()
                    ),
                    nonce = rotateAppAuthKeysSigned.nonce
                  ).bind()
                  val updatedState = state.migrationProgress.withProof(
                    newAppAuthKeys = AppAuthPublicKeys(
                      appGlobalAuthPublicKey = state.newAppGlobalAuthKey,
                      appRecoveryAuthPublicKey = state.newAppRecoveryAuthKey,
                      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
                        result.appGlobalAuthKeyHwSignature
                      )
                    ),
                    proof = HwSignedAction(header)
                  ).withRotationData(
                    hwSignedAccountId = result.hwSignedAccountId,
                    hwAuthPublicKey = result.hwAuthPublicKey,
                    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
                      result.appGlobalAuthKeyHwSignature
                    )
                  )
                  onStateChange(
                    W3UpgradeUiState.RunningAuthRotation(
                      migrationProgress = updatedState
                    )
                  )
                }.logFailure { "Failed to persist W3 device info during resumed auth rotation" }
                  .onFailure { onStateChange(W3UpgradeUiState.Error) }
              },
              onCancel = { onStateChange(W3UpgradeUiState.Error) },
              onError = wrongHardwareErrorHandler(
                expectedType = HardwareType.W3,
                retryState = state,
                onStateChange = onStateChange
              ),
              hardwareVerification = HardwareVerification.NotRequired,
              hardwareTypeOverride = HardwareType.W3,
              screenPresentationStyle = ScreenPresentationStyle.Modal,
              eventTrackerContext = NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION,
              confirmationContent = HardwareConfirmationContent.SignActionProof
            )
          )
        } else {
          nfcConfirmableSessionUiStateMachine.model(
            NfcConfirmableSessionUIStateMachineProps(
              session = { session, commands ->
                commands.verifyHardwareType(session, expectedType = HardwareType.W3)
                w3DeviceInfo = commands.getDeviceInfo(session)
                commands.upgradeRotateAppAuthKeys(
                  session = session,
                  params = UpgradeRotateAppAuthKeysParams(
                    accountId = props.account.accountId.serverId,
                    appGlobalAuthPublicKey = state.newAppGlobalAuthKey.value
                  )
                )
              },
              onSuccess = { result: UpgradeRotateAppAuthKeysResult ->
                coroutineBinding {
                  // First post-commitment W3 tap — persist identity so
                  // validateHardwareIsPaired has the W3 serial.
                  w3DeviceInfo?.let { firmwareDeviceInfoDao.setDeviceInfo(it).bind() }
                  val updatedState = state.migrationProgress.withRotationData(
                    hwSignedAccountId = result.hwSignedAccountId,
                    hwAuthPublicKey = result.hwAuthPublicKey,
                    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
                      result.appGlobalAuthKeyHwSignature
                    )
                  )
                  onStateChange(
                    W3UpgradeUiState.RunningAuthRotation(
                      migrationProgress = updatedState
                    )
                  )
                }.logFailure { "Failed to persist W3 device info during upgrade" }
                  .onFailure { onStateChange(W3UpgradeUiState.Error) }
              },
              onCancel = { onStateChange(W3UpgradeUiState.Error) },
              onError = wrongHardwareErrorHandler(
                expectedType = HardwareType.W3,
                retryState = state,
                onStateChange = onStateChange
              ),
              hardwareVerification = HardwareVerification.NotRequired,
              hardwareTypeOverride = HardwareType.W3,
              screenPresentationStyle = ScreenPresentationStyle.Modal,
              eventTrackerContext = NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION,
              confirmationContent = HardwareConfirmationContent.SignActionProof
            )
          )
        }
      }
      is W3UpgradeUiState.RunningAuthRotation -> {
        LaunchedEffect(Unit) {
          migrationService.proceed(state.migrationProgress)
            .onSuccess { nextState ->
              eventTracker.track(AnalyticsAction.ACTION_APP_W3_UPGRADE_AUTH_ROTATED)
              onStateChange(
                uiStateForMigrationProgress(nextState)
                  ?: W3UpgradeUiState.Error
              )
            }
            .onFailure {
              onStateChange(W3UpgradeUiState.Error)
            }
        }

        w3LoadingScreenModel(
          "Rotating auth keys...",
          WalletMigrationEventTrackerScreenId.W3_UPGRADE_RUNNING_AUTH_ROTATION
        )
      }
      is W3UpgradeUiState.PreparingUpgradeAuthorization -> {
        // Build both action proof payloads (with app co-signing) and fetch DDK keypair.
        LaunchedEffect(Unit) {
          ensureFreshGlobalTokensForActionProof(props.account)
            .onSuccess {
              val appAuthKey = state.migrationProgress.currentKeybox.activeAppKeyBundle.authKey
              val keysetId = state.migrationProgress.next().newKeyset.f8eSpendingKeyset.keysetId

              val result = coroutineBinding {
                val dbSigned = actionProofService.buildAppSignedPayload(
                  action = Action.UPDATE_DESCRIPTOR_BACKUPS,
                  extra = emptyMap(),
                  appAuthKey = appAuthKey,
                  accountId = props.account.accountId
                ).bind()

                val akSigned = actionProofService.buildAppSignedPayload(
                  action = Action.ROTATE_SPENDING_KEYSET,
                  extra = mapOf("eid" to keysetId),
                  appAuthKey = appAuthKey,
                  accountId = props.account.accountId
                ).bind()

                val ddkKeypair = relationshipsKeysRepository
                  .getKeyWithPrivateMaterialOrCreate<DelegatedDecryptionKey>()
                  .bind()

                Triple(dbSigned, akSigned, ddkKeypair)
              }

              result
                .onSuccess { (dbSigned, akSigned, ddkKeypair) ->
                  onStateChange(
                    W3UpgradeUiState.AuthorizingW3Upgrade(
                      migrationProgress = state.migrationProgress,
                      descriptorBackupsSigned = dbSigned,
                      activateKeysetSigned = akSigned,
                      ddkPrivateKeyBytes = ddkKeypair.privateKey.bytes
                    )
                  )
                }
                .onFailure {
                  onStateChange(W3UpgradeUiState.Error)
                }
            }
            .onFailure {
              onStateChange(W3UpgradeUiState.Error)
            }
        }
        w3LoadingScreenModel(
          "Preparing authorization...",
          WalletMigrationEventTrackerScreenId.W3_UPGRADE_PREPARING_AUTHORIZATION
        )
      }
      is W3UpgradeUiState.AuthorizingW3Upgrade -> {
        // Single confirmable NFC tap: signs both action proofs + seals DDK.
        nfcConfirmableSessionUiStateMachine.model(
          NfcConfirmableSessionUIStateMachineProps(
            session = { session, commands ->
              commands.verifyHardwareType(session, expectedType = HardwareType.W3)
              commands.upgradeAuthorizeW3(
                session = session,
                ddkPrivateKeyBytes = state.ddkPrivateKeyBytes,
                sealedSsekForDecryption = state.migrationProgress.sealedSsekForDecryption,
                descriptorBackupsBindings = state.descriptorBackupsSigned.bindings,
                activateKeysetBindings = state.activateKeysetSigned.bindings,
                actionProofVersion = ACTION_PROOF_VERSION
              )
            },
            onSuccess = { result: UpgradeAuthorizeW3Result ->
              // Create action proof headers from HW + app signatures.
              coroutineBinding {
                val sealedSsekForDecryption = state.migrationProgress.sealedSsekForDecryption
                if (sealedSsekForDecryption != null) {
                  val unsealedSsek = result.unsealedSsek
                    ?: return@coroutineBinding Err(
                      IllegalStateException(
                        "Resumed W3 upgrade expected unsealed SSEK from upgradeAuthorizeW3"
                      )
                    ).bind<Unit>()
                  ssekDao.set(
                    sealedSsekForDecryption,
                    Sek(SymmetricKeyImpl(unsealedSsek))
                  ).bind()
                }

                val dbHeader = actionProofService.createActionProofHeader(
                  signatures = listOf(
                    state.descriptorBackupsSigned.appSignature,
                    result.descriptorBackupsSignature
                  ),
                  nonce = state.descriptorBackupsSigned.nonce
                ).bind()
                val akHeader = actionProofService.createActionProofHeader(
                  signatures = listOf(
                    state.activateKeysetSigned.appSignature,
                    result.activateKeysetSignature
                  ),
                  nonce = state.activateKeysetSigned.nonce
                ).bind()

                val descriptorBackupWithProof =
                  state.migrationProgress.withProof(HwSignedAction(dbHeader))
                val serverKeysetActivation =
                  descriptorBackupWithProof.next().withProof(HwSignedAction(akHeader))

                onStateChange(
                  W3UpgradeUiState.RunningServerKeysetActivation(
                    migrationProgress = serverKeysetActivation,
                    pendingDescriptorBackup = descriptorBackupWithProof,
                    sealedDdkData = result.sealedDdkData
                  )
                )
              }.logFailure { "Failed to create action proof headers for W3 upgrade" }
                .onFailure { onStateChange(W3UpgradeUiState.Error) }
            },
            onCancel = { onStateChange(W3UpgradeUiState.Error) },
            onError = wrongHardwareErrorHandler(
              expectedType = HardwareType.W3,
              retryState = state,
              onStateChange = onStateChange
            ),
            hardwareVerification = HardwareVerification.NotRequired,
            hardwareTypeOverride = HardwareType.W3,
            segment = PrivateWalletMigrationAppSegment,
            actionDescription = "Authorize wallet upgrade",
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION,
            confirmationContent = HardwareConfirmationContent.SignActionProof
          )
        )
      }

      is W3UpgradeUiState.RunningServerKeysetActivation -> {
        LaunchedEffect(Unit) {
          // If both proofs were collected upfront, run the descriptor backup first.
          val pendingBackup = state.pendingDescriptorBackup
          if (pendingBackup != null) {
            migrationService.proceed(pendingBackup)
              .onFailure {
                onStateChange(W3UpgradeUiState.Error)
                return@LaunchedEffect
              }
          }

          val keysetActivationResult = migrationService.proceed(state.migrationProgress)
            .onFailure {
              onStateChange(W3UpgradeUiState.Error)
              return@LaunchedEffect
            }
            .get() ?: run {
            onStateChange(W3UpgradeUiState.Error)
            return@LaunchedEffect
          }

          // Upload DDK alongside the other network calls.
          val mp = state.migrationProgress
          val ddkBackup = MigrationProgress.DdkBackup(
            type = mp.type,
            currentKeybox = mp.currentKeybox,
            newKeyset = mp.newKeyset,
            sealedDdkData = state.sealedDdkData,
            sealedCsek = mp.sealedCsek
          )
          migrationService.proceed(ddkBackup)
            .onFailure {
              onStateChange(W3UpgradeUiState.Error)
              return@LaunchedEffect
            }

          // Pass sealedDdkData to provisioning so it knows to skip DDK after proceed.
          val uiState = when {
            keysetActivationResult is MigrationProgress.HardwareDescriptorProvisioning ->
              W3UpgradeUiState.ProvisioningHardwareDescriptor(
                migrationProgress = keysetActivationResult
              )
            else ->
              uiStateForMigrationProgress(keysetActivationResult)
          }
          onStateChange(uiState ?: W3UpgradeUiState.Error)
        }

        w3LoadingScreenModel(
          message = if (state.pendingDescriptorBackup != null) {
            "Uploading backups and activating keyset..."
          } else {
            "Activating spending keyset..."
          },
          id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_RUNNING_SERVER_KEYSET_ACTIVATION
        )
      }
      is W3UpgradeUiState.ProvisioningHardwareDescriptor -> {
        provisionHardwareDescriptorModel(
          state = state,
          onStateChange = onStateChange
        )
      }
      is W3UpgradeUiState.ShowingWrongHardwareError -> {
        wrongHardwareErrorModel(
          state = state,
          onStateChange = onStateChange
        )
      }
      else -> error("Unexpected state in AuthRotationPhaseModel: $state")
    }

  @Composable
  private fun BackupAndSweepPhaseModel(
    state: W3UpgradeUiState,
    props: W3UpgradeUiProps,
    scope: CoroutineScope,
    resumedFromCloudBackupFlow: Boolean,
    onStateChange: (W3UpgradeUiState) -> Unit,
  ): ScreenModel =
    when (state) {
      is W3UpgradeUiState.CloudBackup -> {
        fullAccountCloudSignInAndBackupUiStateMachine.model(
          FullAccountCloudSignInAndBackupProps(
            sealedCsek = state.sealedCsek,
            keybox = state.keybox,
            onBackupFailed = { onStateChange(W3UpgradeUiState.Error) },
            onBackupSaved = {
              scope.launch {
                migrationService.proceed(state.migrationProgress)
                  .onSuccess { nextState ->
                    val fingerprint = migrationService.getOldHardwareFingerprint().get()
                    if (fingerprint != null) {
                      onStateChange(
                        W3UpgradeUiState.CheckingForFunds(
                          keybox = state.keybox,
                          migrationProgress = nextState as? MigrationProgress.LocalKeyboxActivation,
                          oldHardwareFingerprint = fingerprint
                        )
                      )
                    } else {
                      onStateChange(W3UpgradeUiState.Error)
                    }
                  }
                  .onFailure {
                    onStateChange(W3UpgradeUiState.Error)
                  }
              }
            },
            presentationStyle = ScreenPresentationStyle.Modal,
            requireAuthRefreshForCloudBackup = false,
            isSkipCloudBackupInstructions = true
          )
        )
      }
      is W3UpgradeUiState.CheckingForFunds -> {
        LaunchedEffect(Unit) {
          migrationService.estimateMigrationFees(props.account, state.oldHardwareFingerprint)
            .onSuccess {
              // There are funds to sweep — show old hardware instructions
              onStateChange(
                W3UpgradeUiState.ShowingOldHardwareInstructions(
                  keybox = state.keybox,
                  migrationProgress = state.migrationProgress,
                  oldHardwareFingerprint = state.oldHardwareFingerprint
                )
              )
            }
            .onFailure { error ->
              when (error) {
                is MigrationError.InsufficientFundsForMigration -> {
                  // No funds to sweep — skip sweep entirely
                  onStateChange(proceedAfterSweepPhase(state.migrationProgress))
                }
                else -> {
                  // Fee estimation failed (transient) — block and show error
                  onStateChange(W3UpgradeUiState.Error)
                }
              }
            }
        }

        w3LoadingScreenModel(
          "Checking wallet balance...",
          WalletMigrationEventTrackerScreenId.W3_UPGRADE_CHECKING_FOR_FUNDS
        )
      }
      is W3UpgradeUiState.ShowingOldHardwareInstructions -> {
        ScreenModel(
          body = W3UpgradeOldHardwareInstructionsBodyModel(
            onBack = null,
            step = if (resumedFromCloudBackupFlow) 3 else 4,
            totalSteps = if (resumedFromCloudBackupFlow) 3 else 4,
            onContinue = {
              onStateChange(
                W3UpgradeUiState.Sweeping(
                  keybox = state.keybox,
                  migrationProgress = state.migrationProgress,
                  oldHardwareFingerprint = state.oldHardwareFingerprint
                )
              )
            }
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }
      is W3UpgradeUiState.Sweeping -> {
        sweepUiStateMachine.model(
          SweepUiProps(
            account = FullAccount(state.keybox.fullAccountId, state.keybox),
            sweepContext = SweepContext.W3Upgrade(
              replacedHardwareFingerprint = state.oldHardwareFingerprint
            ),
            presentationStyle = ScreenPresentationStyle.Modal,
            onExit = null,
            onSuccess = {
              scope.launch {
                onStateChange(proceedAfterSweepPhase(state.migrationProgress))
              }
            },
            hasAttemptedSweep = false,
            onAttemptSweep = {}
          )
        )
      }
      else -> error("Unexpected state in BackupAndSweepPhaseModel: $state")
    }

  /**
   * Resolves the initial UI state by checking for an in-progress W3 upgrade migration.
   * Returns whether the migration is in progress and the appropriate starting UI state.
   */
  private suspend fun resolveInitialUiState(): ResolvedInitialState {
    val progress = migrationService.resume(MigrationType.W3Upgrade).get()
      ?: return ResolvedInitialState(false, false, W3UpgradeUiState.ShowingIntro)
    val inProgress = progress.isInProgress()
    val resumedFromCloudBackup = progress.wasResumedFromCloudBackup()
    if (!inProgress) return ResolvedInitialState(false, false, W3UpgradeUiState.ShowingIntro)
    val state = uiStateForMigrationProgress(progress)
      ?: W3UpgradeUiState.ShowingIntro
    return ResolvedInitialState(true, resumedFromCloudBackup, state)
  }

  /**
   * Maps a [MigrationProgress] to the corresponding [W3UpgradeUiState].
   * Returns null for unhandled progress types, letting callers choose a fallback.
   */
  private suspend fun uiStateForMigrationProgress(progress: MigrationProgress): W3UpgradeUiState? {
    return when (progress) {
      is MigrationProgress.NotStarted ->
        W3UpgradeUiState.ShowingIntro.takeIf { progress.resumedFromCloudBackup }
      is MigrationProgress.DdkBackup -> {
        // On resume, rewind to the composite tap to re-collect proofs + seal DDK.
        // This replays descriptor backup + keyset activation, which is safe (see comments below).
        W3UpgradeUiState.PreparingUpgradeAuthorization(
          migrationProgress = MigrationProgress.DescriptorBackup(
            type = progress.type,
            currentKeybox = progress.currentKeybox,
            newKeyset = progress.newKeyset,
            sealedCsek = progress.sealedCsek
          )
        )
      }
      is MigrationProgress.CloudBackup -> {
        W3UpgradeUiState.CloudBackup(
          sealedCsek = progress.sealedCsek,
          keybox = progress.currentKeybox,
          migrationProgress = progress
        )
      }
      is MigrationProgress.LocalKeyboxActivation -> {
        val fingerprint = migrationService.getOldHardwareFingerprint().get()
        if (fingerprint != null) {
          W3UpgradeUiState.CheckingForFunds(
            keybox = progress.currentKeybox,
            migrationProgress = progress,
            oldHardwareFingerprint = fingerprint
          )
        } else {
          W3UpgradeUiState.Error
        }
      }
      is MigrationProgress.AuthKeyRotation -> {
        val authKeys = progress.newAppAuthKeys
        if (progress.canRetryWithOldHardwareProofOnly()) {
          W3UpgradeUiState.AttemptingResumedAuthKeyRotation(progress)
        } else if (progress.resumedFromCloudBackup && authKeys != null) {
          W3UpgradeUiState.PreparingNewHardwareRotation(
            migrationProgress = progress,
            newAppGlobalAuthKey = authKeys.appGlobalAuthPublicKey,
            newAppRecoveryAuthKey = authKeys.appRecoveryAuthPublicKey
          )
        } else if (progress.resumedFromCloudBackup) {
          W3UpgradeUiState.GeneratingAuthKeys(resumedAuthRotation = progress)
        } else {
          W3UpgradeUiState.ShowingOldHardwareInstructionsForAuthorization(
            resumedAuthRotation = progress,
            newAppGlobalAuthKey = progress.newAppAuthKeys?.appGlobalAuthPublicKey,
            newAppRecoveryAuthKey = progress.newAppAuthKeys?.appRecoveryAuthPublicKey
          )
        }
      }
      is MigrationProgress.DescriptorBackup -> {
        W3UpgradeUiState.PreparingUpgradeAuthorization(migrationProgress = progress)
      }
      is MigrationProgress.ServerKeysetActivation -> {
        // On resume, rewind to the composite tap to re-collect proofs + seal DDK.
        // This replays the descriptor backup upload, which is safe because:
        //  - uploads are idempotent (already-uploaded keysets are filtered out)
        //  - sealedSsek is persisted in onboardingKeyboxSealedSsekDao (cleared only after DDK backup checkpoint)
        W3UpgradeUiState.PreparingUpgradeAuthorization(
          migrationProgress = MigrationProgress.DescriptorBackup(
            type = progress.type,
            currentKeybox = progress.currentKeybox,
            newKeyset = progress.newKeyset,
            sealedCsek = progress.sealedCsek
          )
        )
      }
      is MigrationProgress.HardwareDescriptorProvisioning -> {
        // On resume, rewind to the composite tap to re-collect proofs + seal DDK.
        // Same idempotency guarantees as ServerKeysetActivation above.
        W3UpgradeUiState.PreparingUpgradeAuthorization(
          migrationProgress = MigrationProgress.DescriptorBackup(
            type = progress.type,
            currentKeybox = progress.currentKeybox,
            newKeyset = progress.newKeyset,
            sealedCsek = progress.sealedCsek
          )
        )
      }
      is MigrationProgress.Completed -> W3UpgradeUiState.Success
      else -> null
    }
  }

  /**
   * Proceeds past the sweep phase (or skipped sweep) by advancing the migration.
   * Routes to auth key rotation if needed, or completes the upgrade.
   */
  private suspend fun proceedAfterSweepPhase(
    migrationProgress: MigrationProgress.LocalKeyboxActivation?,
  ): W3UpgradeUiState {
    if (migrationProgress == null) return W3UpgradeUiState.Success
    return migrationService.proceed(migrationProgress)
      .get()
      ?.let { nextState ->
        when (nextState) {
          is MigrationProgress.Completed -> W3UpgradeUiState.Success
          else -> W3UpgradeUiState.Error
        }
      } ?: W3UpgradeUiState.Error
  }

  /**
   * NFC session that provisions the hardware descriptor on W3. DDK is always
   * pre-uploaded via the composite tap, so this skips past the DDK backup state.
   */
  @Composable
  private fun provisionHardwareDescriptorModel(
    state: W3UpgradeUiState.ProvisioningHardwareDescriptor,
    onStateChange: (W3UpgradeUiState) -> Unit,
  ): ScreenModel {
    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        session = { session, commands ->
          commands.verifyHardwareType(session, expectedType = HardwareType.W3)
          coroutineBinding<AppGlobalAuthKeyHwSignature, Throwable> {
            val response = state.migrationProgress.signedKeysResponse
            val newKeyset = state.migrationProgress.newKeyset

            // Verify WSM signature over the 5 public keys before presenting to hardware.
            wsmVerifier.verifyPublicKeysOrLog(
              appAuthPubHex = response.appAuthPub,
              hardwareAuthPubHex = response.hardwareAuthPub,
              appSpendingPubHex = response.appSpendingPub,
              hardwareSpendingPubHex = response.hardwareSpendingPub,
              serverSpendingPubHex = response.serverSpendingPub,
              signature = response.signature,
              f8eEnvironment = state.migrationProgress.currentKeybox.config.f8eEnvironment,
              context = "W3 upgrade build hardware descriptor"
            )

            val appSpendingKey = response.appSpendingPub.decodeHexResult("app spending key").bind()
            val appAuthKey = response.appAuthPub.decodeHexResult("app auth key").bind()
            val serverSpendingKey =
              response.serverSpendingPub.decodeHexResult("server spending key").bind()
            val wsmSignature = response.signature.decodeHexResult("WSM signature").bind()

            val appSpendingKeyChaincode = chaincodeExtractor
              .extractChaincode(newKeyset.appKey.key.xpub)
              .result.bind()

            val serverSpendingXpub =
              ensureNotNull(newKeyset.f8eSpendingKeyset.privateWalletRootXpub) {
                IllegalStateException("Server spending xpub is required for W3 upgrade provisioning")
              }
            val serverSpendingKeyChaincode = chaincodeExtractor
              .extractChaincode(serverSpendingXpub)
              .result.bind()

            val accountIndex = newKeyset.hardwareKey.key.extractAccountIndex()
            val networkMainnet =
              state.migrationProgress.currentKeybox.config.bitcoinNetworkType == BitcoinNetworkType.BITCOIN

            val signature = commands.verifyKeysAndBuildDescriptor(
              session = session,
              appSpendingKey = appSpendingKey,
              appSpendingKeyChaincode = appSpendingKeyChaincode,
              networkMainnet = networkMainnet,
              appAuthKey = appAuthKey,
              serverSpendingKey = serverSpendingKey,
              serverSpendingKeyChaincode = serverSpendingKeyChaincode,
              wsmSignature = wsmSignature,
              accountIndex = accountIndex
            )

            AppGlobalAuthKeyHwSignature(signature)
          }
        },
        onSuccess = { result ->
          val signature = result.get()
          if (signature == null) {
            onStateChange(W3UpgradeUiState.Error)
            return@NfcSessionUIStateMachineProps
          }

          // Proceed through hardware descriptor provisioning (local DB).
          migrationService.proceed(state.migrationProgress.withSignature(signature))
            .onFailure {
              onStateChange(W3UpgradeUiState.Error)
              return@NfcSessionUIStateMachineProps
            }
            .onSuccess { nextState ->
              // DDK was already uploaded in the composite tap — skip past DdkBackup.
              val finalState = if (nextState is MigrationProgress.DdkBackup) {
                nextState.next()
              } else {
                nextState
              }
              onStateChange(
                uiStateForMigrationProgress(finalState)
                  ?: W3UpgradeUiState.Error
              )
            }
        },
        onCancel = { onStateChange(W3UpgradeUiState.Error) },
        onError = wrongHardwareErrorHandler(
          expectedType = HardwareType.W3,
          retryState = state,
          onStateChange = onStateChange
        ),
        hardwareVerification = HardwareVerification.Required(),
        hardwareTypeOverride = HardwareType.W3,
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        eventTrackerContext = NfcEventTrackerScreenIdContext.VERIFY_KEYS_AND_BUILD_HARDWARE_DESCRIPTOR,
        showDeviceConfirmation = true
      )
    )
  }

  /**
   * Renders the "Wrong Bitkey tapped" error screen with a Retry button
   * that returns the user to the appropriate retry state.
   */
  private fun wrongHardwareErrorModel(
    state: W3UpgradeUiState.ShowingWrongHardwareError,
    onStateChange: (W3UpgradeUiState) -> Unit,
  ): ScreenModel {
    val errorMessage = NfcErrorMessage.fromException(
      NfcException.WrongHardwareType(
        expected = state.expectedHardwareType,
        // actual is not used by NfcErrorMessage — only expected determines the message.
        actual = state.expectedHardwareType
      )
    )
    return ScreenModel(
      body = ErrorFormBodyModel(
        title = errorMessage.title,
        subline = errorMessage.description,
        primaryButton = ButtonDataModel(
          text = "Retry",
          onClick = { onStateChange(state.retryState) }
        ),
        eventTrackerScreenId = WalletMigrationEventTrackerScreenId.W3_UPGRADE_WRONG_HARDWARE_ERROR
      )
    )
  }

  /**
   * Creates an error handler for NFC sessions that routes [NfcException.WrongHardwareType]
   * and [NfcException.UnpairedHardwareError] to [W3UpgradeUiState.ShowingWrongHardwareError]
   * for retry, letting other errors fall through.
   *
   * [NfcException.UnpairedHardwareError] is also handled because sessions using
   * [NfcSessionUIStateMachineProps.HardwareVerification.Required] will reject an
   * unpaired device (e.g. the old W1) before [verifyHardwareType] runs.
   */
  private fun wrongHardwareErrorHandler(
    expectedType: HardwareType,
    retryState: W3UpgradeUiState,
    onStateChange: (W3UpgradeUiState) -> Unit,
  ): (NfcException) -> Boolean =
    { exception ->
      if (exception is NfcException.WrongHardwareType ||
        exception is NfcException.UnpairedHardwareError
      ) {
        onStateChange(
          W3UpgradeUiState.ShowingWrongHardwareError(
            expectedHardwareType = expectedType,
            retryState = retryState
          )
        )
        true
      } else {
        false
      }
    }

  private suspend fun ensureFreshGlobalTokensForActionProof(
    account: FullAccount,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      authTokensService.refreshAccessTokenWithApp(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        scope = Global
      )
        .mapError { it as Throwable }
        .bind()
    }
}

private fun authKeysForRotation(
  account: FullAccount,
  resumedAuthRotation: MigrationProgress.AuthKeyRotation?,
  generatedRecoveryAuthKey: PublicKey<AppRecoveryAuthKey>?,
): Pair<PublicKey<AppGlobalAuthKey>, PublicKey<AppRecoveryAuthKey>> {
  val persistedAuthKeys = resumedAuthRotation?.newAppAuthKeys
  return (
    persistedAuthKeys?.appGlobalAuthPublicKey ?: account.keybox.activeAppKeyBundle.authKey
  ) to (
    persistedAuthKeys?.appRecoveryAuthPublicKey ?: requireNotNull(generatedRecoveryAuthKey) {
      "A new recovery auth key is required when no persisted auth rotation data exists"
    }
  )
}

@Composable
private fun IntroPhaseModel(
  state: W3UpgradeUiState,
  props: W3UpgradeUiProps,
  scope: CoroutineScope,
  isMigrationInProgress: Boolean,
  resumedFromCloudBackupFlow: Boolean,
  onStateChange: (W3UpgradeUiState) -> Unit,
  firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  bitcoinWalletService: BitcoinWalletService,
  utxoConsolidationUiStateMachine: UtxoConsolidationUiStateMachine,
  utxoMaxConsolidationCountFeatureFlag: UtxoMaxConsolidationCountFeatureFlag,
  cloudBackupHealthRepository: CloudBackupHealthRepository,
  repairCloudBackupStateMachine: RepairCloudBackupStateMachine,
  eventTracker: EventTracker,
): ScreenModel {
  val introOnBack = props.onExit.takeUnless {
    isMigrationInProgress || resumedFromCloudBackupFlow
  }
  return when (state) {
    is W3UpgradeUiState.Loading -> {
      w3LoadingScreenModel("Loading...", WalletMigrationEventTrackerScreenId.W3_UPGRADE_LOADING)
    }
    is W3UpgradeUiState.ShowingIntro -> {
      var isCheckingBackup by remember { mutableStateOf(false) }
      if (isCheckingBackup) {
        LaunchedEffect("checking-backup-health") {
          val appKeyBackupStatus = catchingResult {
            cloudBackupHealthRepository.performSync(
              accountId = props.account.accountId,
              keybox = props.account.keybox
            ).appKeyBackupStatus
          }.getOrElse { error ->
            logWarn(throwable = error) {
              "Failed to sync cloud backup health on W3 upgrade Continue"
            }
            AppKeyBackupStatus.ProblemWithBackup.ConnectivityUnavailable
          }
          isCheckingBackup = false
          when (appKeyBackupStatus) {
            is AppKeyBackupStatus.Healthy -> {
              eventTracker.track(AnalyticsAction.ACTION_APP_W3_UPGRADE_STARTED)
              onStateChange(W3UpgradeUiState.CheckingPendingTransactions)
            }
            is AppKeyBackupStatus.ProblemWithBackup -> onStateChange(
              ShowingCloudBackupUnhealthyWarning(problemWithBackup = appKeyBackupStatus)
            )
          }
        }
      }

      ScreenModel(
        body = W3UpgradeIntroBodyModel(
          onBack = introOnBack,
          isLoading = isCheckingBackup,
          onContinue = {
            isCheckingBackup = true
          }
        ),
        presentationStyle = ScreenPresentationStyle.Modal
      )
    }
    is W3UpgradeUiState.ShowingDeviceReady ->
      showingDeviceReadyScreenModel(
        state = state,
        scope = scope,
        account = props.account,
        firmwareDeviceInfoDao = firmwareDeviceInfoDao,
        resumedFromCloudBackupFlow = resumedFromCloudBackupFlow,
        onStateChange = onStateChange
      )
    is ShowingCloudBackupUnhealthyWarning ->
      cloudBackupUnhealthyWarningScreenModel(
        state = state,
        isMigrationInProgress = isMigrationInProgress,
        onExit = props.onExit,
        onStateChange = onStateChange
      )
    is W3UpgradeUiState.RepairingCloudBackup -> {
      repairCloudBackupStateMachine.model(
        RepairAppKeyBackupProps(
          account = props.account,
          appKeyBackupStatus = state.problemWithBackup,
          presentationStyle = ScreenPresentationStyle.Modal,
          onExit = { onStateChange(W3UpgradeUiState.ShowingIntro) },
          onRepaired = { onStateChange(W3UpgradeUiState.ShowingIntro) }
        )
      )
    }
    is W3UpgradeUiState.CheckingPendingTransactions -> {
      LaunchedEffect("check-pending-transactions-and-utxo-count") {
        val transactionData = bitcoinWalletService.getTransactionData()
        val hasUnconfirmedUtxos = transactionData.utxos.unconfirmed.isNotEmpty()
        if (hasUnconfirmedUtxos) {
          onStateChange(W3UpgradeUiState.ShowingPendingTransactionsWarning)
        } else {
          val utxoCount = transactionData.utxos.confirmed.size
          val maxUtxos = utxoMaxConsolidationCountFeatureFlag.flagValue().value.value.toInt()
          if (maxUtxos in 1..<utxoCount) {
            onStateChange(
              W3UpgradeUiState.ShowingUtxoConsolidationRequired(utxoCount = utxoCount)
            )
          } else {
            onStateChange(W3UpgradeUiState.ShowingDeviceReady())
          }
        }
      }
      ScreenModel(
        body = W3UpgradeIntroBodyModel(
          onBack = introOnBack,
          onContinue = {}
        ),
        presentationStyle = ScreenPresentationStyle.Modal
      )
    }
    is W3UpgradeUiState.ShowingPendingTransactionsWarning -> {
      ScreenModel(
        body = W3UpgradeIntroBodyModel(
          onBack = introOnBack,
          onContinue = {}
        ),
        presentationStyle = ScreenPresentationStyle.Modal,
        bottomSheetModel = SheetModel(
          onClosed = { onStateChange(W3UpgradeUiState.ShowingIntro) },
          body = W3UpgradePendingTransactionsWarningSheetModel(
            onBack = { onStateChange(W3UpgradeUiState.ShowingIntro) },
            onGotIt = { onStateChange(W3UpgradeUiState.ShowingIntro) }
          )
        )
      )
    }
    is W3UpgradeUiState.ShowingUtxoConsolidationRequired -> {
      ScreenModel(
        body = W3UpgradeIntroBodyModel(
          onBack = introOnBack,
          onContinue = {}
        ),
        presentationStyle = ScreenPresentationStyle.Modal,
        bottomSheetModel = SheetModel(
          onClosed = { onStateChange(W3UpgradeUiState.ShowingIntro) },
          body = W3UpgradeUtxoConsolidationRequiredSheetModel(
            onBack = { onStateChange(W3UpgradeUiState.ShowingIntro) },
            onContinue = { onStateChange(W3UpgradeUiState.UtxoConsolidation) }
          )
        )
      )
    }
    is W3UpgradeUiState.UtxoConsolidation -> {
      utxoConsolidationUiStateMachine.model(
        UtxoConsolidationProps(
          account = props.account,
          onConsolidationSuccess = {
            onStateChange(W3UpgradeUiState.ShowingIntro)
          },
          onBack = {
            onStateChange(W3UpgradeUiState.ShowingIntro)
          },
          context = UtxoConsolidationContext.W3Upgrade
        )
      )
    }
    else -> error("Unexpected state in IntroPhaseModel: $state")
  }
}

private fun cloudBackupUnhealthyWarningScreenModel(
  state: ShowingCloudBackupUnhealthyWarning,
  isMigrationInProgress: Boolean,
  onExit: () -> Unit,
  onStateChange: (W3UpgradeUiState) -> Unit,
): ScreenModel {
  val returnToIntro = { onStateChange(W3UpgradeUiState.ShowingIntro) }
  return ScreenModel(
    body = W3UpgradeIntroBodyModel(
      onBack = onExit.takeUnless { isMigrationInProgress },
      onContinue = {}
    ),
    presentationStyle = ScreenPresentationStyle.Modal,
    bottomSheetModel = SheetModel(
      onClosed = returnToIntro,
      body = W3UpgradeCloudBackupUnhealthyWarningSheetModel(
        onBack = returnToIntro,
        onRepair = {
          onStateChange(
            W3UpgradeUiState.RepairingCloudBackup(problemWithBackup = state.problemWithBackup)
          )
        }
      )
    )
  )
}

private fun showingDeviceReadyScreenModel(
  state: W3UpgradeUiState.ShowingDeviceReady,
  scope: CoroutineScope,
  account: FullAccount,
  firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  resumedFromCloudBackupFlow: Boolean,
  onStateChange: (W3UpgradeUiState) -> Unit,
): ScreenModel {
  return ScreenModel(
    body = W3UpgradeDeviceReadyBodyModel(
      onBack = { onStateChange(W3UpgradeUiState.ShowingIntro) }
        .takeUnless { resumedFromCloudBackupFlow },
      step = 1,
      totalSteps = if (resumedFromCloudBackupFlow) 3 else 4,
      onYes = {
        // Read old device identity to carry in memory through the pre-keyset flow.
        // Firmware telemetry is skipped during W3 pairing so FirmwareDeviceInfoDao
        // retains the W1 info. DAO persistence is deferred until after the W1 tap.
        scope.launch {
          val serial = firmwareDeviceInfoDao.getDeviceInfo().get()?.serial
          if (serial == null) {
            onStateChange(W3UpgradeUiState.Error)
            return@launch
          }
          val fingerprint = account.keybox.activeSpendingKeyset
            .hardwareKey.key.origin.fingerprint
          onStateChange(
            W3UpgradeUiState.PairingNewHardware(
              oldDeviceSerial = serial,
              oldHardwareFingerprint = fingerprint
            )
          )
        }
      },
      onNo = {
        onStateChange(state.copy(showingNoDeviceAlert = true))
      }
    ),
    alertModel = if (state.showingNoDeviceAlert) {
      val dismissAlert = { onStateChange(state.copy(showingNoDeviceAlert = false)) }
      ButtonAlertModel(
        title = "A new Bitkey device is required for the upgrade",
        subline = "Visit https://bitkey.world to purchase a new Bitkey device.",
        onDismiss = dismissAlert,
        primaryButtonText = "Got it",
        onPrimaryButtonClick = dismissAlert
      )
    } else {
      null
    },
    presentationStyle = ScreenPresentationStyle.Modal
  )
}

@Composable
private fun TerminalPhaseModel(
  state: W3UpgradeUiState,
  props: W3UpgradeUiProps,
  scope: CoroutineScope,
  isMigrationInProgress: Boolean,
  onStateChange: (W3UpgradeUiState) -> Unit,
  onMigrationInProgress: (Boolean) -> Unit,
  onResumedFromCloudBackupChange: (Boolean) -> Unit,
  accountService: AccountService,
  resolveInitialUiState: suspend () -> ResolvedInitialState,
  eventTracker: EventTracker,
): ScreenModel =
  when (state) {
    is W3UpgradeUiState.Success -> {
      LaunchedEffect(Unit) {
        eventTracker.track(AnalyticsAction.ACTION_APP_W3_UPGRADE_COMPLETE)
      }
      val onComplete: () -> Unit = {
        scope.launch {
          val updatedAccount = accountService.getAccount<FullAccount>()
            .get() ?: props.account
          props.onUpgradeComplete(updatedAccount)
        }
      }
      LaunchedEffect("finish-upgrade") {
        onComplete()
      }
      w3LoadingScreenModel(
        message = "Loading wallet...",
        id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_COMPLETE
      )
    }
    is W3UpgradeUiState.Error -> {
      ScreenModel(
        body = ErrorFormBodyModel(
          title = "Upgrade Error",
          subline = "There was an error upgrading your device. Please try again.",
          primaryButton = ButtonDataModel(
            text = "Retry",
            onClick = {
              scope.launch {
                onStateChange(W3UpgradeUiState.Loading)
                val resolved = resolveInitialUiState()
                onMigrationInProgress(resolved.isMigrationInProgress)
                onResumedFromCloudBackupChange(resolved.resumedFromCloudBackup)
                onStateChange(resolved.uiState)
              }
            }
          ),
          secondaryButton = ButtonDataModel(
            text = "Cancel",
            onClick = props.onExit
          ).takeUnless { isMigrationInProgress },
          eventTrackerScreenId = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR
        ),
        presentationStyle = ScreenPresentationStyle.Modal
      )
    }
    else -> error("Unexpected state in TerminalPhaseModel: $state")
  }

/**
 * Returns true if this migration progress state requires UI interaction,
 * meaning the automatic migration loop should stop and show UI.
 */
private fun MigrationProgress.requiresUiInteraction(): Boolean =
  this is MigrationProgress.AuthKeyRotation ||
    this is MigrationProgress.DescriptorBackup ||
    this is MigrationProgress.ServerKeysetActivation ||
    this is MigrationProgress.HardwareDescriptorProvisioning ||
    this is MigrationProgress.DdkBackup ||
    this is MigrationProgress.CloudBackup ||
    this is MigrationProgress.LocalKeyboxActivation ||
    this is MigrationProgress.Completed

private fun MigrationProgress.AuthKeyRotation.hasResumedRotationData(): Boolean {
  return newAppAuthKeys != null &&
    hwAuthPublicKey != null &&
    hwSignedAccountId != null
}

private fun MigrationProgress.AuthKeyRotation.canRetryWithOldHardwareProofOnly(): Boolean {
  return hasResumedRotationData() && proof == null
}

private fun MigrationProgress.wasResumedFromCloudBackup(): Boolean =
  when (this) {
    is MigrationProgress.NotStarted -> resumedFromCloudBackup
    is MigrationProgress.CreateNewKeyset -> resumedFromCloudBackup
    is MigrationProgress.AuthKeyRotation -> resumedFromCloudBackup
    is MigrationProgress.DescriptorBackup -> resumedFromCloudBackup
    else -> false
  }

private fun w3LoadingScreenModel(
  message: String,
  id: EventTrackerScreenId,
) = ScreenModel(
  body = LoadingSuccessBodyModel(
    state = LoadingSuccessBodyModel.State.Loading,
    message = message,
    id = id,
    primaryButton = null,
    secondaryButton = null
  ),
  presentationStyle = ScreenPresentationStyle.Modal
)

private fun String.decodeHexResult(fieldName: String): Result<ByteString, Throwable> =
  catchingResult { decodeHex() }
    .mapError { cause -> IllegalArgumentException("Invalid $fieldName hex from server", cause) }

private data class ResolvedInitialState(
  val isMigrationInProgress: Boolean,
  val resumedFromCloudBackup: Boolean,
  val uiState: W3UpgradeUiState,
)

private sealed interface W3UpgradeUiState {
  /** Initial loading state while checking for in-progress migration. */
  data object Loading : W3UpgradeUiState

  /** Introduction screen explaining the W3 upgrade. */
  data object ShowingIntro : W3UpgradeUiState

  /** Asking if user has new device ready. */
  data class ShowingDeviceReady(
    val showingNoDeviceAlert: Boolean = false,
  ) : W3UpgradeUiState

  /** Showing warning sheet that cloud backup is unhealthy and blocks the upgrade. */
  data class ShowingCloudBackupUnhealthyWarning(
    val problemWithBackup: AppKeyBackupStatus.ProblemWithBackup,
  ) : W3UpgradeUiState

  /** Running the cloud backup repair flow inline. */
  data class RepairingCloudBackup(
    val problemWithBackup: AppKeyBackupStatus.ProblemWithBackup,
  ) : W3UpgradeUiState

  /** Checking for pending (unconfirmed) transactions before proceeding. */
  data object CheckingPendingTransactions : W3UpgradeUiState

  /** Showing warning sheet that pending transactions block the upgrade. */
  data object ShowingPendingTransactionsWarning : W3UpgradeUiState

  /** Showing sheet that UTXO consolidation is required before upgrade. */
  data class ShowingUtxoConsolidationRequired(val utxoCount: Int) : W3UpgradeUiState

  /** UTXO consolidation flow in progress before upgrading. */
  data object UtxoConsolidation : W3UpgradeUiState

  /** Pairing the new W3 hardware device. Carries old device identity in memory. */
  data class PairingNewHardware(
    val oldDeviceSerial: String,
    val oldHardwareFingerprint: String,
  ) : W3UpgradeUiState

  /**
   * Creating new keyset and starting migration.
   * Runs AFTER the W1 tap (when [w1ProofOfPossession] is set).
   */
  data class CreatingKeyset(
    val fingerprintEnrolled: PairingTransactionResponse.FingerprintEnrolled,
    val newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    val newAppRecoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    val w1ProofOfPossession: HwFactorProofOfPossession?,
    val oldDeviceSerial: String,
    val oldHardwareFingerprint: String,
  ) : W3UpgradeUiState

  /**
   * Preparing the app auth keys used during W3 auth rotation.
   * The normal W3 upgrade reuses the current global app auth key, generates a new recovery auth
   * key, and rotates the hardware auth key.
   * First-time flow carries [fingerprintEnrolled]; resume flow carries [resumedAuthRotation].
   */
  data class GeneratingAuthKeys(
    val fingerprintEnrolled: PairingTransactionResponse.FingerprintEnrolled? = null,
    val resumedAuthRotation: MigrationProgress.AuthKeyRotation? = null,
    val oldDeviceSerial: String? = null,
    val oldHardwareFingerprint: String? = null,
  ) : W3UpgradeUiState

  /**
   * Showing instructions to tap old hardware to authorize auth rotation.
   * Resume flow carries [resumedAuthRotation] so it can reuse any persisted rotation data.
   */
  data class ShowingOldHardwareInstructionsForAuthorization(
    val fingerprintEnrolled: PairingTransactionResponse.FingerprintEnrolled? = null,
    val newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>? = null,
    val newAppRecoveryAuthKey: PublicKey<AppRecoveryAuthKey>? = null,
    val resumedAuthRotation: MigrationProgress.AuthKeyRotation? = null,
    val oldDeviceSerial: String? = null,
    val oldHardwareFingerprint: String? = null,
  ) : W3UpgradeUiState

  /**
   * Tapping old W1 hardware to authorize moving auth from W1 to W3.
   * Resume flow carries [resumedAuthRotation] so it can skip key regeneration or W3 retap.
   */
  data class TappingOldHardwareForAuthorization(
    val fingerprintEnrolled: PairingTransactionResponse.FingerprintEnrolled? = null,
    val newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    val newAppRecoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    val resumedAuthRotation: MigrationProgress.AuthKeyRotation? = null,
    val oldDeviceSerial: String? = null,
    val oldHardwareFingerprint: String? = null,
  ) : W3UpgradeUiState

  /** Attempts to resume auth rotation immediately using persisted rotation data. */
  data class AttemptingResumedAuthKeyRotation(
    val migrationProgress: MigrationProgress.AuthKeyRotation,
  ) : W3UpgradeUiState

  /** Transition state after old-device authorization, before the W3 rotation tap. */
  data class PreparingNewHardwareRotation(
    val migrationProgress: MigrationProgress.AuthKeyRotation,
    val newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    val newAppRecoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    val rotateAppAuthKeysSigned: AppSignedActionProof? = null,
  ) : W3UpgradeUiState

  /** Showing instructions to tap new hardware for auth rotation signatures. */
  data class ShowingNewHardwareInstructionsForRotation(
    val migrationProgress: MigrationProgress.AuthKeyRotation,
    val newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    val newAppRecoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    val rotateAppAuthKeysSigned: AppSignedActionProof? = null,
  ) : W3UpgradeUiState

  /** Tapping new W3 hardware to produce the auth-rotation signatures. */
  data class TappingNewHardwareForRotation(
    val migrationProgress: MigrationProgress.AuthKeyRotation,
    val newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    val newAppRecoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    val rotateAppAuthKeysSigned: AppSignedActionProof? = null,
  ) : W3UpgradeUiState

  /** Running W1-to-W3 auth key rotation on the server and locally. */
  data class RunningAuthRotation(
    val migrationProgress: MigrationProgress.AuthKeyRotation,
  ) : W3UpgradeUiState

  /** Loading bindings + DDK keypair before the composite NFC tap. */
  data class PreparingUpgradeAuthorization(
    val migrationProgress: MigrationProgress.DescriptorBackup,
  ) : W3UpgradeUiState

  /** Running the composite upgradeAuthorizeW3 confirmable NFC command. */
  data class AuthorizingW3Upgrade(
    val migrationProgress: MigrationProgress.DescriptorBackup,
    val descriptorBackupsSigned: AppSignedActionProof,
    val activateKeysetSigned: AppSignedActionProof,
    val ddkPrivateKeyBytes: ByteString,
  ) : W3UpgradeUiState

  /** Activating the spending keyset after collecting the W3 proof. */
  data class RunningServerKeysetActivation(
    val migrationProgress: MigrationProgress.ServerKeysetActivation,
    /** When non-null, descriptor backup proceed() runs first (proofs were batched). */
    val pendingDescriptorBackup: MigrationProgress.DescriptorBackup? = null,
    /** Pre-sealed DDK data from the composite tap — uploaded alongside other network calls. */
    val sealedDdkData: build.wallet.crypto.SealedData,
  ) : W3UpgradeUiState

  /** Verifying keys and provisioning the descriptor on W3 hardware. */
  data class ProvisioningHardwareDescriptor(
    val migrationProgress: MigrationProgress.HardwareDescriptorProvisioning,
  ) : W3UpgradeUiState

  /** Wrong hardware type tapped — shows retry error screen. */
  data class ShowingWrongHardwareError(
    val expectedHardwareType: HardwareType,
    val retryState: W3UpgradeUiState,
  ) : W3UpgradeUiState

  /** Running cloud backup. */
  data class CloudBackup(
    val sealedCsek: SealedCsek?,
    val keybox: Keybox,
    val migrationProgress: MigrationProgress.CloudBackup,
  ) : W3UpgradeUiState

  /** Checking whether there are funds to sweep before prompting for old hardware. */
  data class CheckingForFunds(
    val keybox: Keybox,
    val migrationProgress: MigrationProgress.LocalKeyboxActivation?,
    val oldHardwareFingerprint: String,
  ) : W3UpgradeUiState

  /** Showing instructions to use old hardware for sweep. */
  data class ShowingOldHardwareInstructions(
    val keybox: Keybox,
    val migrationProgress: MigrationProgress.LocalKeyboxActivation?,
    val oldHardwareFingerprint: String,
  ) : W3UpgradeUiState

  /** Sweeping funds from old wallet to new wallet. */
  data class Sweeping(
    val keybox: Keybox,
    val migrationProgress: MigrationProgress.LocalKeyboxActivation?,
    val oldHardwareFingerprint: String,
  ) : W3UpgradeUiState

  /** Upgrade completed successfully. */
  data object Success : W3UpgradeUiState

  /** Upgrade failed with error. */
  data object Error : W3UpgradeUiState

  /** Confirming exit from the upgrade flow (user doesn't have their old Bitkey). */
  data class ConfirmingExit(
    val previousState: W3UpgradeUiState,
  ) : W3UpgradeUiState
}
