@file:Suppress("CyclomaticComplexMethod")

package build.wallet.statemachine.data.recovery.inprogress

import androidx.compose.runtime.*
import bitkey.account.*
import bitkey.auth.AuthTokenScope
import bitkey.backup.DescriptorBackup
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.privilegedactions.ActionProofService
import bitkey.recovery.*
import bitkey.recovery.DelayNotifyCancellationRequest.CancelLostAppAndCloudRecovery
import bitkey.recovery.DelayNotifyCancellationRequest.CancelLostHardwareRecovery
import bitkey.recovery.DescriptorBackupService.SsekUnsealCheckResult
import build.wallet.auth.AuthProtocolError
import build.wallet.auth.AuthTokensService
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.extractAccountIndex
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.challange.DelayNotifyChallenge
import build.wallet.bitkey.challange.SignedChallenge
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.*
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SealedData
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.crypto.firmwareSealedDataValidationError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.auth.PrivilegedActionProof.HwKeyProof
import build.wallet.f8e.auth.PrivilegedActionProof.HwSignedAction
import build.wallet.f8e.recovery.ServerRecovery
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.fwup.FirmwareDataService
import build.wallet.fwup.semverToInt
import build.wallet.ktor.result.HttpError
import build.wallet.nfc.transaction.*
import build.wallet.platform.random.UuidGenerator
import build.wallet.recovery.CancelDelayNotifyRecoveryError
import build.wallet.recovery.LocalRecoveryAttemptProgress
import build.wallet.recovery.LocalRecoveryAttemptProgress.CompletionAttemptFailedDueToServerCancellation
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.*
import build.wallet.relationships.DelegatedDecryptionKeyService
import build.wallet.relationships.RelationshipsKeysRepository
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.data.recovery.inprogress.KeysetState.Complete
import build.wallet.statemachine.data.recovery.inprogress.KeysetState.Incomplete
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.CreatingSpendingKeysWithF8EData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.FailedToCreateSpendingKeysData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.ProcessingDescriptorBackupsData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.*
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.nonNegativeDurationBetween
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import uniffi.actionproof.Action
import kotlin.time.Duration

/**
 * Represents the state of keysets for creating a new keybox during recovery.
 */
sealed interface KeysetState {
  /**
   * No descriptor backups were performed, so we only have the active keyset.
   */
  object Incomplete : KeysetState

  /**
   * Descriptor backups were performed and we have a complete list of keysets.
   */
  data class Complete(val keysets: List<SpendingKeyset>) : KeysetState
}

@Suppress("LargeClass")
@BitkeyInject(AppScope::class)
class RecoveryInProgressDataStateMachineImpl(
  private val actionProofService: ActionProofService,
  private val delayNotifyService: DelayNotifyService,
  private val clock: Clock,
  private val sekGenerator: SekGenerator,
  private val csekDao: CsekDao,
  private val ssekDao: SsekDao,
  private val uuidGenerator: UuidGenerator,
  private val recoveryStatusService: RecoveryStatusService,
  private val delegatedDecryptionKeyService: DelegatedDecryptionKeyService,
  private val relationshipsKeysRepository: RelationshipsKeysRepository,
  private val relationshipsService: RelationshipsService,
  private val minimumLoadingDuration: MinimumLoadingDuration,
  private val accountConfigService: AccountConfigService,
  private val descriptorBackupService: DescriptorBackupService,
  private val provisionAppAuthKeyTransactionProvider: ProvisionAppAuthKeyTransactionProvider,
  private val minFirmwareVersionFeatureFlag: FingerprintResetMinFirmwareVersionFeatureFlag,
  private val firmwareDataService: FirmwareDataService,
  private val authTokensService: AuthTokensService,
) : RecoveryInProgressDataStateMachine {
  @Composable
  override fun model(props: RecoveryInProgressProps): RecoveryInProgressData {
    // This state machine is completely self-contained - it calculates initial state once
    // and handles all transitions manually. External recovery state changes do NOT reset
    // the state machine to prevent interrupting ongoing flows.
    var state by remember {
      mutableStateOf(
        calculateInitialState(props.recovery)
      )
    }

    return when (val dataState = state) {
      is WaitingForDelayPeriodState -> {
        // Suspend until delay period is finished.
        LaunchedEffect("check-delay-period") {
          delay(dataState.remainingDelayPeriod)
          state = ReadyToCompleteRecoveryState
        }
        WaitingForRecoveryDelayPeriodData(
          factorToRecover = props.recovery.factorToRecover,
          delayPeriodStartTime = dataState.delayPeriodStartTime,
          delayPeriodEndTime = dataState.delayPeriodEndTime,
          cancel = {
            state = getHwProofOfPossessionOrCancelDirectly(
              props = props,
              rollbackFromAwaitingProofOfPossession = {
                state = dataState
              }
            )
          }
        )
      }

      is ReadyToCompleteRecoveryState -> {
        ReadyToCompleteRecoveryData(
          // Only allow to cancel recovery when it's initiated (while delay period is pending, or has finished),
          // or when the auth keys have been successfully rotated. If a customer is in the process of
          // rotating auth keys, we don't want to allow them to cancel recovery, since the server may
          // have already rotated, and the customer would be in a bad state. This prevents a bad state
          // in case if some parts of the completion process have already started, but failed to complete
          // for some reason (F8e or NFC error).
          canCancelRecovery = props.recovery is InitiatedRecovery || props.recovery is RotatedAuthKeys,
          startComplete = {
            state = AwaitingChallengeAndSeksSignedWithHardwareState(
              challenge = DelayNotifyChallenge.fromParts(
                type = DelayNotifyChallenge.Type.RECOVERY,
                app = props.recovery.appGlobalAuthKey,
                recovery = props.recovery.appRecoveryAuthKey,
                hw = props.recovery.hardwareAuthKey
              )
            )
          },
          cancel = {
            state =
              getHwProofOfPossessionOrCancelDirectly(
                props = props,
                rollbackFromAwaitingProofOfPossession = {
                  state = dataState
                }
              )
          },
          physicalFactor = props.recovery.factorToRecover
        )
      }

      is RotatingAuthTokensState -> {
        LaunchedEffect("rotate-auth-tokens") {
          delayNotifyService
            .rotateAuthTokens()
            .onSuccess {
              state = CreatingSpendingKeysWithF8eState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek
              )
            }
            .onFailure { error ->
              state = FailedToRotateAuthState(cause = error)
            }
        }
        RotatingAuthKeysWithF8eData(props.recovery.factorToRecover)
      }

      is CheckCompletionAttemptForSuccessOrCancellation -> {
        LaunchedEffect("checking auth") {
          withMinimumDelay(minimumLoadingDuration.value) {
            delayNotifyService.verifyAuthKeysAfterRotation()
          }
            .onSuccess {
              state = RotatingAuthTokensState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek
              )
            }
            .onFailure { error ->
              when (error) {
                is AuthProtocolError -> recoveryStatusService.setLocalRecoveryProgress(
                  CompletionAttemptFailedDueToServerCancellation
                )

                else -> state = FailedToRotateAuthState(cause = error)
              }
            }
        }
        CheckingCompletionAttemptData(
          physicalFactor = props.recovery.factorToRecover
        )
      }

      is VerifyingNotificationCommsForCancellationState -> {
        VerifyingNotificationCommsForCancellationData(
          lostFactor = props.recovery.factorToRecover,
          fullAccountId = props.recovery.fullAccountId,
          onRollback = {
            // Take them back to the beginning
            state = calculateInitialState(props.recovery)
          },
          onComplete = {
            state =
              getHwProofOfPossessionOrCancelDirectly(
                props,
                rollbackFromAwaitingProofOfPossession = {
                  state = calculateInitialState(props.recovery)
                }
              )
          }
        )
      }

      is CancellingState -> {
        LaunchedEffect("cancelling-recovery") {
          delayNotifyService.cancelDelayNotify(dataState.cancellationRequest)
            .onFailure { error ->
              state =
                if (error.isNeedsCommsVerificationError()) {
                  VerifyingNotificationCommsForCancellationState
                } else {
                  FailedToCancelRecoveryState(
                    cause = error,
                    isNetworkError = error.isNetworkError()
                  )
                }
            }
        }

        return CancellingData(props.recovery.factorToRecover)
      }

      is ResolvingHardwareTypeForCancellationState -> {
        LaunchedEffect("resolve-hardware-type") {
          val hardwareType = firmwareDataService.firmwareData()
            .value
            .firmwareDeviceInfo
            ?.hardwareType()
            ?: HardwareType.W1
          state = AwaitingCancellationProofOfPossessionState(
            rollback = dataState.rollback,
            hardwareType = hardwareType
          )
        }
        CancellingData(props.recovery.factorToRecover)
      }

      is AwaitingCancellationProofOfPossessionState -> {
        AwaitingProofOfPossessionForCancellationData(
          appAuthKey = props.recovery.appGlobalAuthKey,
          hardwareType = dataState.hardwareType,
          addProof = {
            state = CancellingState(CancelLostAppAndCloudRecovery(it))
          },
          rollback = dataState.rollback,
          fullAccountId = props.recovery.fullAccountId
        )
      }

      is AwaitingChallengeAndSeksSignedWithHardwareState -> {
        if (isW3Hardware()) {
          // Generate CSEK/SSEK once and store in state so they survive recomposition.
          val csek = dataState.csek
          val ssek = dataState.ssek
          if (csek == null || ssek == null) {
            LaunchedEffect("generate-seks") {
              state = dataState.copy(
                csek = sekGenerator.generate(),
                ssek = sekGenerator.generate()
              )
            }
            // Return the "ready to complete" screen while keys are being generated,
            // but allow the user to cancel/back out while they wait.
            ReadyToCompleteRecoveryData(
              canCancelRecovery = true,
              startComplete = {},
              cancel = { state = ReadyToCompleteRecoveryState },
              physicalFactor = props.recovery.factorToRecover
            )
          } else {
            AwaitingChallengeAndCsekSignedWithHardwareData(
              nfcSession = W3SignChallengeAndSealSeks(
                challenge = dataState.challenge,
                csek = csek,
                ssek = ssek,
                success = { response ->
                  coroutineBinding {
                    response.sealedSsek.firmwareSealedDataValidationError()?.let {
                        validationError ->
                      Err(
                        IllegalArgumentException(
                          "Invalid sealed SSEK from hardware tap 1: $validationError"
                        )
                      ).bind<Unit>()
                    }
                    csekDao.set(response.sealedCsek, response.csek).bind()
                    ssekDao.set(response.sealedSsek, response.ssek).bind()
                  }.onSuccess {
                    state = RotatingAuthKeysWithF8eState(
                      sealedCsek = response.sealedCsek,
                      sealedSsek = response.sealedSsek,
                      hardwareSignedChallenge = response.signedChallenge
                    )
                  }.onFailure { error ->
                    state = FailedToRotateAuthState(cause = error)
                  }
                },
                failure = { state = ReadyToCompleteRecoveryState }
              ).toConfirmable()
            )
          }
        } else {
          AwaitingChallengeAndCsekSignedWithHardwareData(
            nfcSession = RecoveryNfcSession.Standard(
              SignChallengeAndSealSeks(
                challenge = dataState.challenge,
                success = { response ->
                  coroutineBinding {
                    response.sealedSsek.firmwareSealedDataValidationError()?.let {
                        validationError ->
                      Err(
                        IllegalArgumentException(
                          "Invalid sealed SSEK from hardware tap 1: $validationError"
                        )
                      ).bind<Unit>()
                    }
                    csekDao.set(response.sealedCsek, response.csek).bind()
                    ssekDao.set(response.sealedSsek, response.ssek).bind()
                  }.onSuccess {
                    state = RotatingAuthKeysWithF8eState(
                      sealedCsek = response.sealedCsek,
                      sealedSsek = response.sealedSsek,
                      hardwareSignedChallenge = response.signedChallenge
                    )
                  }.onFailure { error ->
                    state = FailedToRotateAuthState(cause = error)
                  }
                },
                failure = { state = ReadyToCompleteRecoveryState },
                sekGenerator = sekGenerator
              )
            )
          )
        }
      }

      is FailedToRotateAuthState -> FailedToRotateAuthData(
        cause = dataState.cause,
        factorToRecover = props.recovery.factorToRecover,
        onConfirm = { state = ReadyToCompleteRecoveryState }
      )

      is RotatingAuthKeysWithF8eState -> {
        LaunchedEffect("rotate-auth-keys") {
          val rotationResult = delayNotifyService.verifyAuthKeysAfterRotation()
            .andThenRecover {
              // If verification failed, perform the rotation
              delayNotifyService.rotateAuthKeys(
                hardwareSignedChallenge = dataState.hardwareSignedChallenge,
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek
              )
            }

          rotationResult.onSuccess {
            state = RotatingAuthTokensState(
              sealedCsek = dataState.sealedCsek,
              sealedSsek = dataState.sealedSsek
            )
          }
            .onFailure { error ->
              state = FailedToRotateAuthState(cause = error)
            }
        }

        RotatingAuthKeysWithF8eData(props.recovery.factorToRecover)
      }

      is DelegatedDecryptionKeyErrorState -> {
        DelegatedDecryptionKeyErrorStateData(
          cause = dataState.cause,
          physicalFactor = props.recovery.factorToRecover,
          onContinue = {
            state =
              RemovingTrustedContactsState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset
              )
          },
          onRetry = {
            state = PreparingProofAndKeyTransferState(
              sealedCsek = dataState.sealedCsek,
              sealedSsek = dataState.sealedSsek,
              f8eSpendingKeyset = dataState.f8eSpendingKeyset
            )
          }
        )
      }

      is RemovingTrustedContactsState -> {
        LaunchedEffect("remove-trusted-contacts") {
          delayNotifyService.removeTrustedContacts()
            .onSuccess {
              state =
                PreparingProofAndKeyTransferState(
                  sealedCsek = dataState.sealedCsek,
                  sealedSsek = dataState.sealedSsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset
                )
            }
            .onFailure {
              state =
                PreparingProofAndKeyTransferState(
                  sealedCsek = dataState.sealedCsek,
                  sealedSsek = dataState.sealedSsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset
                )
            }
        }

        RemovingTrustedContactsData(
          physicalFactor = props.recovery.factorToRecover
        )
      }

      is CreatingSpendingKeysWithF8eState -> {
        LaunchedEffect("create-spending-keys") {
          delayNotifyService.createSpendingKeyset()
            .onSuccess { f8eSpendingKeyset ->
              state = PreparingProofAndKeyTransferState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek,
                f8eSpendingKeyset = f8eSpendingKeyset
              )
            }
            .onFailure { error ->
              state = FailedToCreateSpendingKeysState(
                cause = error,
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek
              )
            }
        }
        CreatingSpendingKeysWithF8EData(props.recovery.factorToRecover)
      }

      is ProcessingDescriptorBackupsState -> {
        LaunchedEffect("prepare-descriptor-backups") {
          descriptorBackupService.prepareDescriptorBackupsForRecovery(
            accountId = props.recovery.fullAccountId,
            factorToRecover = props.recovery.factorToRecover,
            f8eSpendingKeyset = dataState.f8eSpendingKeyset,
            appSpendingKey = props.recovery.appSpendingKey,
            hwSpendingKey = props.recovery.hardwareSpendingKey
          )
            .onSuccess { preparedData ->
              when (preparedData) {
                is DescriptorBackupPreparedData.Available -> {
                  state = UploadingDescriptorBackupsState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsekForEncryption = dataState.sealedSsek,
                    sealedSsekForDecryption = preparedData.sealedSsek,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    descriptorBackupsProof = dataState.descriptorBackupsProof,
                    activateKeysetProof = dataState.activateKeysetProof,
                    descriptorsToDecrypt = preparedData.descriptorsToDecrypt,
                    keysetsToEncrypt = preparedData.keysetsToEncrypt,
                    sealedDdkResult = dataState.sealedDdkResult
                  )
                }
                is DescriptorBackupPreparedData.EncryptOnly -> {
                  state = UploadingDescriptorBackupsState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsekForEncryption = dataState.sealedSsek,
                    sealedSsekForDecryption = null,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    descriptorBackupsProof = dataState.descriptorBackupsProof,
                    activateKeysetProof = dataState.activateKeysetProof,
                    descriptorsToDecrypt = emptyList(),
                    keysetsToEncrypt = preparedData.keysetsToEncrypt,
                    sealedDdkResult = dataState.sealedDdkResult
                  )
                }
                is DescriptorBackupPreparedData.NeedsUnsealed -> {
                  // Unreachable: SSEK check in PreparingProofAndKeyTransferState now
                  // hard-fails, so tap 2 always unseals when needed.
                  state = FailedToProcessDescriptorBackupsState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsek,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    cause = Error("Unexpected NeedsUnsealed after SSEK check"),
                    descriptorBackupsProof = dataState.descriptorBackupsProof,
                    activateKeysetProof = dataState.activateKeysetProof,
                    sealedDdkResult = dataState.sealedDdkResult
                  )
                }
              }
            }
            .onFailure { error ->
              state = FailedToProcessDescriptorBackupsState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                cause = error,
                descriptorBackupsProof = dataState.descriptorBackupsProof,
                activateKeysetProof = dataState.activateKeysetProof,
                sealedDdkResult = dataState.sealedDdkResult
              )
            }
        }
        HandlingDescriptorEncryption(props.recovery.factorToRecover)
      }

      is FailedToProcessDescriptorBackupsState -> FailedToProcessDescriptorBackupsData(
        physicalFactor = props.recovery.factorToRecover,
        cause = dataState.cause,
        onRetry = {
          state = ProcessingDescriptorBackupsState(
            sealedCsek = dataState.sealedCsek,
            sealedSsek = dataState.sealedSsek,
            descriptorBackupsProof = dataState.descriptorBackupsProof,
            activateKeysetProof = dataState.activateKeysetProof,
            f8eSpendingKeyset = dataState.f8eSpendingKeyset,
            sealedDdkResult = dataState.sealedDdkResult
          )
        }
      )

      is ActivatingSpendingKeysetState -> {
        LaunchedEffect("activate-spending-keyset") {
          delayNotifyService.activateSpendingKeyset(
            keyset = dataState.f8eSpendingKeyset,
            proof = dataState.activateKeysetProof
          )
            .onSuccess { signedKeysResponse ->
              // Hardware descriptor validation is only needed for W3 hardware.
              // We derive the hardware type from firmware device info (populated
              // from earlier NFC taps) rather than accountConfigService, because
              // on a fresh install (lost-app recovery) there's no active account
              // and the default config would incorrectly fall back to W1.
              val isW3 = firmwareDataService.firmwareData().value
                .firmwareDeviceInfo?.hardwareType() == HardwareType.W3
              val hasPrivateWalletXpub = dataState.f8eSpendingKeyset.privateWalletRootXpub != null
              state = if (isW3 && hasPrivateWalletXpub) {
                // W3 account requires signed keys for hardware descriptor validation
                if (signedKeysResponse != null) {
                  BuildingHardwareDescriptorState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsek,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    keysetState = dataState.keysetState,
                    signedKeysResponse = signedKeysResponse,
                    sealedDdkResult = dataState.sealedDdkResult
                  )
                } else {
                  // W3 private wallet must have signed keys - treat as activation failure
                  FailedToActivateSpendingKeysetState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsek,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    activateKeysetProof = dataState.activateKeysetProof,
                    keysetState = dataState.keysetState,
                    cause = Error("W3 keyset activation did not return signed keys for descriptor validation"),
                    sealedDdkResult = dataState.sealedDdkResult
                  )
                }
              } else {
                // W1: check if firmware supports provisioning (unified into BuildingHardwareDescriptor)
                provisionOrPerformDdkBackup(
                  sealedCsek = dataState.sealedCsek,
                  sealedSsek = dataState.sealedSsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                  keysetState = dataState.keysetState,
                  sealedDdkResult = dataState.sealedDdkResult,
                  hwSignature = props.recovery.appGlobalAuthKeyHwSignature
                )
              }
            }
            .onFailure { error ->
              state = FailedToActivateSpendingKeysetState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                activateKeysetProof = dataState.activateKeysetProof,
                keysetState = dataState.keysetState,
                cause = Error(error),
                sealedDdkResult = dataState.sealedDdkResult
              )
            }
        }
        ActivatingSpendingKeysetData(props.recovery.factorToRecover)
      }

      is FailedToActivateSpendingKeysetState -> FailedToActivateSpendingKeysetData(
        physicalFactor = props.recovery.factorToRecover,
        cause = dataState.cause,
        onRetry = {
          state = ActivatingSpendingKeysetState(
            sealedCsek = dataState.sealedCsek,
            sealedSsek = dataState.sealedSsek,
            f8eSpendingKeyset = dataState.f8eSpendingKeyset,
            activateKeysetProof = dataState.activateKeysetProof,
            keysetState = dataState.keysetState,
            sealedDdkResult = dataState.sealedDdkResult
          )
        }
      )

      is FailedToCreateSpendingKeysState -> FailedToCreateSpendingKeysData(
        physicalFactor = props.recovery.factorToRecover,
        cause = dataState.cause,
        onRetry = {
          state =
            CreatingSpendingKeysWithF8eState(
              sealedCsek = dataState.sealedCsek,
              sealedSsek = dataState.sealedSsek
            )
        }
      )

      is FailedPerformingCloudBackupState -> FailedPerformingCloudBackupData(
        keybox = dataState.keybox,
        physicalFactor = props.recovery.factorToRecover,
        cause = dataState.cause,
        retry = {
          state = PerformingCloudBackupState(
            sealedCsek = dataState.sealedCsek,
            keybox = dataState.keybox
          )
        }
      )

      is FailedRegeneratingTcCertificatesState -> {
        FailedRegeneratingTcCertificatesData(
          physicalFactor = props.recovery.factorToRecover,
          cause = dataState.cause,
          retry = {
            state = RegeneratingTcCertificatesState(
              sealedCsek = dataState.sealedCsek,
              f8eSpendingKeyset = dataState.f8eSpendingKeyset,
              keysetState = dataState.keysetState,
              hwSignature = dataState.hwSignature
            )
          }
        )
      }

      is PerformingDdkBackupState -> {
        if (dataState.sealedDdkResult != null) {
          // DDK was pre-sealed during proof-and-key-transfer tap — just upload
          LaunchedEffect("upload-pre-sealed-ddk") {
            coroutineBinding {
              delegatedDecryptionKeyService.uploadSealedDelegatedDecryptionKeyData(
                props.recovery.fullAccountId,
                dataState.sealedDdkResult
              ).bind()
              recoveryStatusService.setLocalRecoveryProgress(LocalRecoveryAttemptProgress.DdkBackedUp)
                .bind()
            }.onSuccess {
              state = RegeneratingTcCertificatesState(
                sealedCsek = dataState.sealedCsek,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                keysetState = dataState.keysetState,
                hwSignature = dataState.hwSignature
              )
            }.onFailure {
              state = FailedPerformingDdkBackupState(
                sealedCsek = dataState.sealedCsek,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                keysetState = dataState.keysetState,
                cause = it,
                delegatedDecryptionKey = null,
                sealedDdkResult = dataState.sealedDdkResult,
                hwSignature = dataState.hwSignature
              )
            }
          }
          PerformingDdkBackupData(physicalFactor = props.recovery.factorToRecover)
        } else if (props.recovery.factorToRecover != Hardware) {
          LaunchedEffect("set-recovery-progress-ddk-backed-up") {
            // If we're not doing a hardware recovery, we don't need
            // to reseal+upload the DDK, so we can mark as complete
            recoveryStatusService.setLocalRecoveryProgress(LocalRecoveryAttemptProgress.DdkBackedUp)
              .onSuccess {
                state = RegeneratingTcCertificatesState(
                  sealedCsek = dataState.sealedCsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                  keysetState = dataState.keysetState,
                  hwSignature = dataState.hwSignature
                )
              }
          }

          PerformingDdkBackupData(
            physicalFactor = props.recovery.factorToRecover
          )
        } else if (dataState.delegatedDecryptionKey == null) {
          LaunchedEffect("get-or-create-ddk") {
            relationshipsKeysRepository.getKeyWithPrivateMaterialOrCreate<DelegatedDecryptionKey>()
              .onSuccess { keypair ->
                state = PerformingDdkBackupState(
                  sealedCsek = dataState.sealedCsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                  keysetState = dataState.keysetState,
                  delegatedDecryptionKey = keypair,
                  sealedDdkResult = dataState.sealedDdkResult,
                  hwSignature = dataState.hwSignature
                )
              }
              .onFailure { error ->
                state = FailedPerformingDdkBackupState(
                  sealedCsek = dataState.sealedCsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                  keysetState = dataState.keysetState,
                  cause = error,
                  delegatedDecryptionKey = null,
                  sealedDdkResult = dataState.sealedDdkResult,
                  hwSignature = dataState.hwSignature
                )
              }
          }

          PerformingDdkBackupData(
            physicalFactor = props.recovery.factorToRecover
          )
        } else {
          SealingDelegatedDecryptionKeyData(
            nfcTransaction = SealDelegatedDecryptionKey(
              unsealedKeypair = dataState.delegatedDecryptionKey,
              success = { sealedDataResult ->
                coroutineBinding {
                  delegatedDecryptionKeyService.uploadSealedDelegatedDecryptionKeyData(
                    props.recovery.fullAccountId,
                    sealedDataResult.sealedData
                  ).bind()

                  recoveryStatusService.setLocalRecoveryProgress(LocalRecoveryAttemptProgress.DdkBackedUp)
                    .bind()
                }.onSuccess {
                  state = RegeneratingTcCertificatesState(
                    sealedCsek = dataState.sealedCsek,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    keysetState = dataState.keysetState,
                    hwSignature = dataState.hwSignature
                  )
                }
                  .onFailure {
                    state = FailedPerformingDdkBackupState(
                      sealedCsek = dataState.sealedCsek,
                      f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                      keysetState = dataState.keysetState,
                      cause = it,
                      delegatedDecryptionKey = dataState.delegatedDecryptionKey,
                      sealedDdkResult = null,
                      hwSignature = dataState.hwSignature
                    )
                  }
              },
              failure = {
                state = FailedPerformingDdkBackupState(
                  sealedCsek = dataState.sealedCsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                  keysetState = dataState.keysetState,
                  cause = Error("NFC Error"),
                  delegatedDecryptionKey = dataState.delegatedDecryptionKey,
                  sealedDdkResult = null,
                  hwSignature = dataState.hwSignature
                )
              }
            )
          )
        }
      }

      is FailedPerformingDdkBackupState ->
        FailedPerformingDdkBackupData(
          physicalFactor = props.recovery.factorToRecover,
          cause = dataState.cause,
          retry = {
            state = PerformingDdkBackupState(
              sealedCsek = dataState.sealedCsek,
              f8eSpendingKeyset = dataState.f8eSpendingKeyset,
              keysetState = dataState.keysetState,
              delegatedDecryptionKey = dataState.delegatedDecryptionKey,
              sealedDdkResult = dataState.sealedDdkResult,
              hwSignature = dataState.hwSignature
            )
          }
        )

      is PerformingCloudBackupState -> PerformingCloudBackupData(
        sealedCsek = dataState.sealedCsek,
        keybox = dataState.keybox,
        onBackupFinished = {
          recoveryStatusService
            .setLocalRecoveryProgress(LocalRecoveryAttemptProgress.BackedUpToCloud)
            .onSuccess {
              state = PerformingSweepState(
                hasAttemptedSweep = false,
                keybox = dataState.keybox
              )
            }
        },
        onBackupFailed = { error ->
          state = FailedPerformingCloudBackupState(
            cause = error,
            sealedCsek = dataState.sealedCsek,
            keybox = dataState.keybox
          )
        }
      )

      is PerformingSweepState -> PerformingSweepData(
        hasAttemptedSweep = dataState.hasAttemptedSweep,
        physicalFactor = props.recovery.factorToRecover,
        keybox = dataState.keybox,
        rollback = {
          state =
            ExitedPerformingSweepState(
              keybox = dataState.keybox
            )
        },
        onCompletionFailed = { cause ->
          state = FailedToCompleteRecoveryState(
            keybox = dataState.keybox,
            cause = cause
          )
        }
      )

      is ExitedPerformingSweepState ->
        ExitedPerformingSweepData(
          physicalFactor = props.recovery.factorToRecover,
          retry = {
            state =
              PerformingSweepState(
                hasAttemptedSweep = false,
                keybox = dataState.keybox
              )
          }
        )

      is FailedToCompleteRecoveryState ->
        FailedToCompleteRecoveryData(
          physicalFactor = props.recovery.factorToRecover,
          cause = dataState.cause,
          retry = {
            state = PerformingSweepState(
              hasAttemptedSweep = true,
              keybox = dataState.keybox
            )
          }
        )

      is FailedToCancelRecoveryState ->
        FailedToCancelRecoveryData(
          recoveredFactor = props.recovery.factorToRecover,
          isNetworkError = dataState.isNetworkError,
          cause = dataState.cause,
          onAcknowledge = {
            state = ReadyToCompleteRecoveryState
          }
        )

      is RegeneratingTcCertificatesState -> {
        LaunchedEffect("regenerate-tc-certificates") {
          val keybox = createNewKeybox(
            recovery = props.recovery,
            f8eSpendingKeyset = dataState.f8eSpendingKeyset,
            keysetState = dataState.keysetState,
            hardwareSpendingKey = props.recovery.hardwareSpendingKey,
            appGlobalAuthKeyHwSignature = dataState.hwSignature
          )

          delayNotifyService.regenerateTrustedContactCertificates(props.oldAppGlobalAuthKey)
            .onSuccess {
              state = PerformingCloudBackupState(
                dataState.sealedCsek,
                keybox = keybox
              )
            }
            .onFailure {
              state = FailedRegeneratingTcCertificatesState(
                cause = it,
                sealedCsek = dataState.sealedCsek,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                keysetState = dataState.keysetState,
                hwSignature = dataState.hwSignature
              )
            }
        }
        RegeneratingTcCertificatesData
      }

      is UploadingDescriptorBackupsState -> {
        LaunchedEffect("upload-descriptor-backups") {
          coroutineBinding {
            val keysets = descriptorBackupService.uploadDescriptorBackups(
              accountId = props.recovery.fullAccountId,
              sealedSsekForEncryption = dataState.sealedSsekForEncryption,
              sealedSsekForDecryption = dataState.sealedSsekForDecryption,
              appAuthKey = props.recovery.appGlobalAuthKey,
              proof = dataState.descriptorBackupsProof,
              descriptorsToDecrypt = dataState.descriptorsToDecrypt,
              keysetsToEncrypt = dataState.keysetsToEncrypt
            ).mapError { Error("Failed to process descriptor backups: $it") }
              .bind()

            recoveryStatusService
              .setLocalRecoveryProgress(
                LocalRecoveryAttemptProgress.UploadedDescriptorBackups(
                  keysets
                )
              ).bind()

            keysets
          }
            .onSuccess { keysets ->
              state = ActivatingSpendingKeysetState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsekForEncryption,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                activateKeysetProof = dataState.activateKeysetProof,
                keysetState = Complete(keysets),
                sealedDdkResult = dataState.sealedDdkResult
              )
            }
            .onFailure { error ->
              state = FailedToProcessDescriptorBackupsState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsekForEncryption,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                cause = error,
                descriptorBackupsProof = dataState.descriptorBackupsProof,
                activateKeysetProof = dataState.activateKeysetProof,
                sealedDdkResult = dataState.sealedDdkResult
              )
            }
        }

        UploadingDescriptorBackupsData(props.recovery.factorToRecover)
      }

      is BuildingHardwareDescriptorState -> {
        if (dataState.signedKeysResponse != null) {
          // W3: descriptor validation NFC
          // Get account config to determine network type
          val accountConfig = accountConfigService.activeOrDefaultConfig().value
          val bitcoinNetworkType = when (accountConfig) {
            is FullAccountConfig -> accountConfig.bitcoinNetworkType
            is DefaultAccountConfig -> accountConfig.toFullAccountConfig().bitcoinNetworkType
            else -> BitcoinNetworkType.BITCOIN // default to mainnet
          }

          BuildingHardwareDescriptorData(
            signedKeysResponse = dataState.signedKeysResponse,
            appSpendingKeyXpub = props.recovery.appSpendingKey.key.xpub,
            serverPrivateWalletRootXpub = dataState.f8eSpendingKeyset.privateWalletRootXpub,
            networkType = bitcoinNetworkType,
            f8eEnvironment = accountConfig.f8eEnvironment,
            accountIndex = props.recovery.hardwareSpendingKey.key.extractAccountIndex(),
            onSuccess = { hwSignature ->
              state = PersistingHwDescriptorValidationState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                keysetState = dataState.keysetState,
                sealedDdkResult = dataState.sealedDdkResult,
                hwSignature = hwSignature
              )
            },
            onFailure = { error ->
              state = FailedToBuildHardwareDescriptorState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                keysetState = dataState.keysetState,
                cause = Error(error.message ?: "Hardware descriptor validation failed"),
                sealedDdkResult = dataState.sealedDdkResult
              )
            }
          )
        } else {
          // W1: provisioning NFC (unified into BuildingHardwareDescriptor for checkpoint reuse)
          ProvisioningAppAuthKeyToHardwareData(
            nfcTransaction = provisionAppAuthKeyTransactionProvider(
              appGlobalAuthPublicKey = props.recovery.appGlobalAuthKey,
              onSuccess = {
                state = PersistingHwDescriptorValidationState(
                  sealedCsek = dataState.sealedCsek,
                  sealedSsek = dataState.sealedSsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                  keysetState = dataState.keysetState,
                  sealedDdkResult = dataState.sealedDdkResult,
                  hwSignature = props.recovery.appGlobalAuthKeyHwSignature
                )
              },
              onCancel = {
                state = FailedToBuildHardwareDescriptorState(
                  sealedCsek = dataState.sealedCsek,
                  sealedSsek = dataState.sealedSsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                  keysetState = dataState.keysetState,
                  cause = Error("Cancelled provisioning app auth key to hardware"),
                  sealedDdkResult = dataState.sealedDdkResult
                )
              }
            )
          )
        }
      }

      is PersistingHwDescriptorValidationState -> {
        LaunchedEffect("persist-hw-descriptor-validated") {
          // Save the HW signature to the recovery so it later gets saved to the new keybox
          // in createNewKeybox
          recoveryStatusService.setLocalRecoveryProgress(
            LocalRecoveryAttemptProgress.HwDescriptorValidated(
              appGlobalAuthKeyHwSignature = dataState.hwSignature,
              sealedDdkData = dataState.sealedDdkResult
            )
          ).onSuccess {
            state = PerformingDdkBackupState(
              sealedCsek = dataState.sealedCsek,
              f8eSpendingKeyset = dataState.f8eSpendingKeyset,
              keysetState = dataState.keysetState,
              delegatedDecryptionKey = null,
              sealedDdkResult = dataState.sealedDdkResult,
              hwSignature = dataState.hwSignature
            )
          }.onFailure { error ->
            state = FailedToBuildHardwareDescriptorState(
              sealedCsek = dataState.sealedCsek,
              sealedSsek = dataState.sealedSsek,
              f8eSpendingKeyset = dataState.f8eSpendingKeyset,
              keysetState = dataState.keysetState,
              cause = Error("Failed to persist HW descriptor validation", error),
              sealedDdkResult = dataState.sealedDdkResult
            )
          }
        }
        // Show loading while persisting
        ActivatingSpendingKeysetData(
          physicalFactor = props.recovery.factorToRecover
        )
      }

      is FailedToBuildHardwareDescriptorState -> {
        FailedToBuildHardwareDescriptorData(
          physicalFactor = props.recovery.factorToRecover,
          cause = dataState.cause,
          onRetry = {
            // Retry by going back through the two-tap flow to get a fresh PoP,
            // which will re-activate the keyset and get signed keys for descriptor validation.
            state = PreparingProofAndKeyTransferState(
              sealedCsek = dataState.sealedCsek,
              sealedSsek = dataState.sealedSsek,
              f8eSpendingKeyset = dataState.f8eSpendingKeyset
            )
          }
        )
      }

      is PreparingProofAndKeyTransferState -> {
        LaunchedEffect("prepare-proof-and-key-transfer") {
          val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment

          // 1. Gather prerequisites per factor (before token refresh to keep token fresh)
          when (props.recovery.factorToRecover) {
            App -> {
              val relationships = relationshipsService.getRelationshipsWithoutSyncing(
                accountId = props.recovery.fullAccountId
              ).getOrElse { error ->
                state = DelegatedDecryptionKeyErrorState(
                  cause = Error(error),
                  sealedCsek = dataState.sealedCsek,
                  sealedSsek = dataState.sealedSsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset
                )
                return@LaunchedEffect
              }

              val hasTrustedContacts = relationships.protectedCustomers.isNotEmpty() ||
                relationships.endorsedTrustedContacts.isNotEmpty()

              var sealedDdkData: SealedData? = null
              if (hasTrustedContacts) {
                sealedDdkData = delegatedDecryptionKeyService
                  .getSealedDelegatedDecryptionKeyData(accountId = props.recovery.fullAccountId)
                  .getOrElse { ddkError ->
                    if (ddkError is HttpError.ClientError && ddkError.response.status.value == 404) {
                      // No DDK on F8e but active TCs — must remove TCs
                      state = RemovingTrustedContactsState(
                        sealedCsek = dataState.sealedCsek,
                        sealedSsek = dataState.sealedSsek,
                        f8eSpendingKeyset = dataState.f8eSpendingKeyset
                      )
                      return@LaunchedEffect
                    } else {
                      state = DelegatedDecryptionKeyErrorState(
                        cause = Error(ddkError),
                        sealedCsek = dataState.sealedCsek,
                        sealedSsek = dataState.sealedSsek,
                        f8eSpendingKeyset = dataState.f8eSpendingKeyset
                      )
                      return@LaunchedEffect
                    }
                  }
              }

              // Check SSEK unseal need — must succeed so tap 2 can unseal when needed
              val ssekCheck = descriptorBackupService.checkSsekUnsealingNeeded(
                accountId = props.recovery.fullAccountId,
                factorToRecover = App
              ).getOrElse { error ->
                state = FailedToRotateAuthState(cause = error)
                return@LaunchedEffect
              }

              val sealedSsekForDecryption = when (ssekCheck) {
                is SsekUnsealCheckResult.NotNeeded -> null
                is SsekUnsealCheckResult.NeedsUnsealing -> ssekCheck.sealedSsek
              }

              // 2. Refresh access token last — minimizes staleness window before hardware signs it
              val tokens = authTokensService.refreshAccessTokenWithApp(
                f8eEnvironment = f8eEnvironment,
                accountId = props.recovery.fullAccountId,
                scope = AuthTokenScope.Global
              ).getOrElse { error ->
                state = FailedToRotateAuthState(cause = error)
                return@LaunchedEffect
              }

              val w3Bindings = prepareW3Bindings(
                accountId = props.recovery.fullAccountId,
                keysetId = dataState.f8eSpendingKeyset.keysetId
              ).getOrElse {
                state = FailedToRotateAuthState(cause = it)
                return@LaunchedEffect
              }

              state = AwaitingProofAndKeyTransferLostAppState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek,
                accessToken = tokens.accessToken,
                sealedDdkData = sealedDdkData,
                sealedSsekForDecryption = sealedSsekForDecryption,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                descriptorBackupsBindings = w3Bindings?.descriptorBackupsBindings,
                descriptorBackupsNonce = w3Bindings?.descriptorBackupsNonce,
                activateKeysetBindings = w3Bindings?.activateKeysetBindings,
                activateKeysetNonce = w3Bindings?.activateKeysetNonce,
                actionProofVersion = w3Bindings?.actionProofVersion
              )
            }

            Hardware -> {
              val ddkKeypair = relationshipsKeysRepository
                .getKeyWithPrivateMaterialOrCreate<DelegatedDecryptionKey>()
                .getOrElse { error ->
                  state = FailedToRotateAuthState(cause = error)
                  return@LaunchedEffect
                }

              // Refresh access token last — minimizes staleness window before hardware signs it
              val tokens = authTokensService.refreshAccessTokenWithApp(
                f8eEnvironment = f8eEnvironment,
                accountId = props.recovery.fullAccountId,
                scope = AuthTokenScope.Global
              ).getOrElse { error ->
                state = FailedToRotateAuthState(cause = error)
                return@LaunchedEffect
              }

              val w3BindingsHw = prepareW3Bindings(
                accountId = props.recovery.fullAccountId,
                keysetId = dataState.f8eSpendingKeyset.keysetId
              ).getOrElse {
                state = FailedToRotateAuthState(cause = it)
                return@LaunchedEffect
              }

              state = AwaitingProofAndKeyTransferLostHwState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek,
                accessToken = tokens.accessToken,
                ddkKeypair = ddkKeypair,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                descriptorBackupsBindings = w3BindingsHw?.descriptorBackupsBindings,
                descriptorBackupsNonce = w3BindingsHw?.descriptorBackupsNonce,
                activateKeysetBindings = w3BindingsHw?.activateKeysetBindings,
                activateKeysetNonce = w3BindingsHw?.activateKeysetNonce,
                actionProofVersion = w3BindingsHw?.actionProofVersion
              )
            }
          }
        }
        PreparingProofAndKeyTransferData(physicalFactor = props.recovery.factorToRecover)
      }

      is AwaitingProofAndKeyTransferLostAppState -> {
        if (isW3Hardware()) {
          AwaitingProofAndKeyTransferLostAppData(
            nfcSession = RecoveryNfcSession.Confirmable(
              session = { session, commands ->
                commands.recoveryAuthorizeLostApp(
                  session = session,
                  sealedDdkData = dataState.sealedDdkData,
                  sealedSsekForDecryption = dataState.sealedSsekForDecryption,
                  descriptorBackupsBindings = dataState.descriptorBackupsBindings!!,
                  activateKeysetBindings = dataState.activateKeysetBindings!!,
                  actionProofVersion = dataState.actionProofVersion!!
                )
              },
              onSuccess = onSuccess@{ result ->
                // Handle DDK unseal result
                val unsealedDdk = result.unsealedDdkData
                if (unsealedDdk != null) {
                  delegatedDecryptionKeyService.restoreDelegatedDecryptionKey(unsealedDdk)
                    .onFailure {
                      state = DelegatedDecryptionKeyErrorState(
                        cause = Error(it),
                        sealedCsek = dataState.sealedCsek,
                        sealedSsek = dataState.sealedSsek,
                        f8eSpendingKeyset = dataState.f8eSpendingKeyset
                      )
                      return@onSuccess
                    }
                }
                // Handle SSEK unseal result
                val unsealedSsekBytes = result.unsealedSsek
                val sealedSsekForDec = dataState.sealedSsekForDecryption
                if (unsealedSsekBytes != null && sealedSsekForDec != null) {
                  ssekDao.set(sealedSsekForDec, Sek(SymmetricKeyImpl(unsealedSsekBytes)))
                    .onFailure {
                      state = PreparingProofAndKeyTransferState(
                        sealedCsek = dataState.sealedCsek,
                        sealedSsek = dataState.sealedSsek,
                        f8eSpendingKeyset = dataState.f8eSpendingKeyset
                      )
                      return@onSuccess
                    }
                }
                // Convert signatures to ActionProof headers (both HW + app)
                val (descriptorBackupsProof, activateKeysetProof) = createW3ActionProofs(
                  descriptorBackupsSignature = result.descriptorBackupsSignature,
                  descriptorBackupsNonce = dataState.descriptorBackupsNonce!!,
                  descriptorBackupsBindings = dataState.descriptorBackupsBindings!!,
                  activateKeysetSignature = result.activateKeysetSignature,
                  activateKeysetNonce = dataState.activateKeysetNonce!!,
                  activateKeysetBindings = dataState.activateKeysetBindings!!,
                  appAuthKey = props.recovery.appGlobalAuthKey
                ) ?: run {
                  state =
                    FailedToRotateAuthState(cause = Error("Failed to create action proof headers"))
                  return@onSuccess
                }
                state = if (dataState.sealedSsek != null) {
                  ProcessingDescriptorBackupsState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsek,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    descriptorBackupsProof = descriptorBackupsProof,
                    activateKeysetProof = activateKeysetProof,
                    sealedDdkResult = null
                  )
                } else {
                  ActivatingSpendingKeysetState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsek,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    activateKeysetProof = activateKeysetProof,
                    keysetState = Incomplete,
                    sealedDdkResult = null
                  )
                }
              },
              onCancel = {
                state = PreparingProofAndKeyTransferState(
                  sealedCsek = dataState.sealedCsek,
                  sealedSsek = dataState.sealedSsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset
                )
              }
            )
          )
        } else {
          AwaitingProofAndKeyTransferLostAppData(
            nfcSession = RecoveryNfcSession.Standard(
              RecoveryProofAndKeyTransferLostApp(
                accessToken = dataState.accessToken,
                sealedDdkData = dataState.sealedDdkData,
                sealedSsekForDecryption = dataState.sealedSsekForDecryption,
                success = success@{ result ->
                  // DDK unseal failed during the NFC session — show DDK error screen
                  if (result.ddkUnsealFailed) {
                    state = DelegatedDecryptionKeyErrorState(
                      cause = Error("Failed to unseal delegated decryption key"),
                      sealedCsek = dataState.sealedCsek,
                      sealedSsek = dataState.sealedSsek,
                      f8eSpendingKeyset = dataState.f8eSpendingKeyset
                    )
                    return@success
                  }

                  val unsealedDdk = result.unsealedDdkData
                  val unsealedSsek = result.unsealedOldSsek
                  val sealedSsekForDec = dataState.sealedSsekForDecryption
                  // Restore DDK — failure is a DDK error
                  if (unsealedDdk != null) {
                    delegatedDecryptionKeyService.restoreDelegatedDecryptionKey(unsealedDdk)
                      .onFailure {
                        state = DelegatedDecryptionKeyErrorState(
                          cause = Error(it),
                          sealedCsek = dataState.sealedCsek,
                          sealedSsek = dataState.sealedSsek,
                          f8eSpendingKeyset = dataState.f8eSpendingKeyset
                        )
                        return@success
                      }
                  }
                  // Store old SSEK — failure retries from preparing
                  if (unsealedSsek != null && sealedSsekForDec != null) {
                    ssekDao.set(sealedSsekForDec, unsealedSsek)
                      .onFailure {
                        state = PreparingProofAndKeyTransferState(
                          sealedCsek = dataState.sealedCsek,
                          sealedSsek = dataState.sealedSsek,
                          f8eSpendingKeyset = dataState.f8eSpendingKeyset
                        )
                        return@success
                      }
                  }
                  val w1Proof = HwKeyProof(result.hwProofOfPossession)
                  state = if (dataState.sealedSsek != null) {
                    ProcessingDescriptorBackupsState(
                      sealedCsek = dataState.sealedCsek,
                      sealedSsek = dataState.sealedSsek,
                      f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                      descriptorBackupsProof = w1Proof,
                      activateKeysetProof = w1Proof,
                      sealedDdkResult = null
                    )
                  } else {
                    ActivatingSpendingKeysetState(
                      sealedCsek = dataState.sealedCsek,
                      sealedSsek = dataState.sealedSsek,
                      f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                      activateKeysetProof = w1Proof,
                      keysetState = Incomplete,
                      sealedDdkResult = null
                    )
                  }
                },
                failure = {
                  state = PreparingProofAndKeyTransferState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsek,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset
                  )
                }
              )
            )
          )
        }
      }

      is AwaitingProofAndKeyTransferLostHwState -> {
        if (isW3Hardware()) {
          AwaitingProofAndKeyTransferLostHwData(
            nfcSession = RecoveryNfcSession.Confirmable(
              session = { session, commands ->
                commands.recoveryAuthorizeLostHw(
                  session = session,
                  ddkPrivateKeyBytes = dataState.ddkKeypair?.privateKey?.bytes,
                  descriptorBackupsBindings = dataState.descriptorBackupsBindings!!,
                  activateKeysetBindings = dataState.activateKeysetBindings!!,
                  actionProofVersion = dataState.actionProofVersion!!
                )
              },
              onSuccess = onSuccess@{ result ->
                // Convert signatures to ActionProof headers (both HW + app)
                val (descriptorBackupsProof, activateKeysetProof) = createW3ActionProofs(
                  descriptorBackupsSignature = result.descriptorBackupsSignature,
                  descriptorBackupsNonce = dataState.descriptorBackupsNonce!!,
                  descriptorBackupsBindings = dataState.descriptorBackupsBindings!!,
                  activateKeysetSignature = result.activateKeysetSignature,
                  activateKeysetNonce = dataState.activateKeysetNonce!!,
                  activateKeysetBindings = dataState.activateKeysetBindings!!,
                  appAuthKey = props.recovery.appGlobalAuthKey
                ) ?: run {
                  state =
                    FailedToRotateAuthState(cause = Error("Failed to create action proof headers"))
                  return@onSuccess
                }
                state = if (dataState.sealedSsek != null) {
                  ProcessingDescriptorBackupsState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsek,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    descriptorBackupsProof = descriptorBackupsProof,
                    activateKeysetProof = activateKeysetProof,
                    sealedDdkResult = result.sealedDdkData
                  )
                } else {
                  ActivatingSpendingKeysetState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsek,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    activateKeysetProof = activateKeysetProof,
                    keysetState = Incomplete,
                    sealedDdkResult = result.sealedDdkData
                  )
                }
              },
              onCancel = {
                state = PreparingProofAndKeyTransferState(
                  sealedCsek = dataState.sealedCsek,
                  sealedSsek = dataState.sealedSsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset
                )
              }
            )
          )
        } else {
          AwaitingProofAndKeyTransferLostHwData(
            nfcSession = RecoveryNfcSession.Standard(
              RecoveryProofAndKeyTransferLostHw(
                accessToken = dataState.accessToken,
                ddkKeypair = dataState.ddkKeypair,
                success = { result ->
                  val w1Proof = HwKeyProof(result.hwProofOfPossession)
                  state = if (dataState.sealedSsek != null) {
                    ProcessingDescriptorBackupsState(
                      sealedCsek = dataState.sealedCsek,
                      sealedSsek = dataState.sealedSsek,
                      f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                      descriptorBackupsProof = w1Proof,
                      activateKeysetProof = w1Proof,
                      sealedDdkResult = result.sealedDdkData
                    )
                  } else {
                    ActivatingSpendingKeysetState(
                      sealedCsek = dataState.sealedCsek,
                      sealedSsek = dataState.sealedSsek,
                      f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                      activateKeysetProof = w1Proof,
                      keysetState = Incomplete,
                      sealedDdkResult = result.sealedDdkData
                    )
                  }
                },
                failure = {
                  state = PreparingProofAndKeyTransferState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsek,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset
                  )
                }
              )
            )
          )
        }
      }
    }
  }

  private fun createNewKeybox(
    recovery: StillRecovering,
    f8eSpendingKeyset: F8eSpendingKeyset,
    keysetState: KeysetState,
    hardwareSpendingKey: HwSpendingPublicKey,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Keybox {
    val recoveredHardwareType = firmwareDataService.firmwareData().value
      .firmwareDeviceInfo
      ?.hardwareType()
    val accountConfig = when (val config = accountConfigService.activeOrDefaultConfig().value) {
      is DefaultAccountConfig -> {
        // On a fresh install (lost-app recovery) there's no active account, so the default
        // config has hardwareType=null which falls back to W1. Resolve from the paired device.
        val hwType = recoveredHardwareType ?: HardwareType.W1
        config.toFullAccountConfig().copy(hardwareType = hwType)
      }
      is FullAccountConfig -> {
        if (recovery.factorToRecover == Hardware && recoveredHardwareType != null) {
          config.copy(hardwareType = recoveredHardwareType)
        } else {
          config
        }
      }
      is LiteAccountConfig -> error("Lite account config is not supported")
      is SoftwareAccountConfig -> error("Software account config is not supported")
    }

    val (keysets, canUseKeyboxKeysets) = when (keysetState) {
      is Incomplete -> {
        val activeKeyset = SpendingKeyset(
          localId = uuidGenerator.random(),
          f8eSpendingKeyset = f8eSpendingKeyset,
          networkType = accountConfig.bitcoinNetworkType,
          appKey = recovery.appSpendingKey,
          hardwareKey = hardwareSpendingKey
        )
        listOf(activeKeyset) to false
      }
      is Complete -> {
        keysetState.keysets to true
      }
    }

    val activeSpendingKeyset = keysets.find { it.f8eSpendingKeyset == f8eSpendingKeyset }
      ?: error("No matching SpendingKeyset found for f8eSpendingKeyset: ${f8eSpendingKeyset.keysetId}")

    return Keybox(
      localId = uuidGenerator.random(),
      fullAccountId = recovery.fullAccountId,
      activeSpendingKeyset = activeSpendingKeyset,
      appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
      activeAppKeyBundle = AppKeyBundle(
        localId = uuidGenerator.random(),
        spendingKey = recovery.appSpendingKey,
        authKey = recovery.appGlobalAuthKey,
        networkType = accountConfig.bitcoinNetworkType,
        recoveryAuthKey = recovery.appRecoveryAuthKey
      ),
      activeHwKeyBundle = HwKeyBundle(
        localId = uuidGenerator.random(),
        spendingKey = hardwareSpendingKey,
        authKey = recovery.hardwareAuthKey,
        networkType = accountConfig.bitcoinNetworkType
      ),
      config = accountConfig,
      keysets = keysets,
      canUseKeyboxKeysets = canUseKeyboxKeysets
    )
  }

  /**
   * Calculate initial state based on remaining delay period and recovery progress.
   * If delay period is still pending, return [WaitingForDelayPeriodState].
   * Otherwise, we are ready to complete recovery, return [ReadyToCompleteRecoveryState].
   */
  private fun calculateInitialState(recovery: StillRecovering): State {
    return when (recovery) {
      is InitiatedRecovery -> when (
        val remainingDelayPeriod =
          recovery.serverRecovery.remainingDelayPeriod()
      ) {
        Duration.ZERO -> ReadyToCompleteRecoveryState
        else -> WaitingForDelayPeriodState(
          remainingDelayPeriod = remainingDelayPeriod,
          delayPeriodStartTime = recovery.serverRecovery.delayStartTime,
          delayPeriodEndTime = recovery.serverRecovery.delayEndTime,
          fullAccountId = recovery.fullAccountId
        )
      }

      is MaybeNoLongerRecovering -> CheckCompletionAttemptForSuccessOrCancellation(
        sealedCsek = recovery.sealedCsek,
        sealedSsek = recovery.sealedSsek
      )

      is RotatedAuthKeys -> {
        CreatingSpendingKeysWithF8eState(
          sealedCsek = recovery.sealedCsek,
          sealedSsek = recovery.sealedSsek
        )
      }

      is CreatedSpendingKeys -> PreparingProofAndKeyTransferState(
        sealedCsek = recovery.sealedCsek,
        sealedSsek = recovery.sealedSsek,
        f8eSpendingKeyset = recovery.f8eSpendingKeyset
      )

      is UploadedDescriptorBackups -> PreparingProofAndKeyTransferState(
        sealedCsek = recovery.sealedCsek,
        sealedSsek = recovery.sealedSsek,
        f8eSpendingKeyset = recovery.f8eSpendingKeyset
      )

      is ActivatedSpendingKeys -> {
        val keysetState =
          if (recovery.keysets.isNotEmpty()) Complete(recovery.keysets) else Incomplete
        val hasPrivateWalletXpub = recovery.f8eSpendingKeyset.privateWalletRootXpub != null
        if (hasPrivateWalletXpub) {
          PreparingProofAndKeyTransferState(
            sealedCsek = recovery.sealedCsek,
            sealedSsek = recovery.sealedSsek,
            f8eSpendingKeyset = recovery.f8eSpendingKeyset
          )
        } else {
          // W1: check if firmware supports provisioning
          provisionOrPerformDdkBackup(
            sealedCsek = recovery.sealedCsek,
            sealedSsek = recovery.sealedSsek,
            f8eSpendingKeyset = recovery.f8eSpendingKeyset,
            keysetState = keysetState,
            sealedDdkResult = null,
            hwSignature = recovery.appGlobalAuthKeyHwSignature
          )
        }
      }

      // Descriptor validation already completed (persisted) — skip directly to DDK backup
      is HwDescriptorValidated -> PerformingDdkBackupState(
        sealedCsek = recovery.sealedCsek,
        f8eSpendingKeyset = recovery.f8eSpendingKeyset,
        keysetState = if (recovery.keysets.isNotEmpty()) Complete(recovery.keysets) else Incomplete,
        delegatedDecryptionKey = null,
        sealedDdkResult = recovery.sealedDdkData,
        hwSignature = recovery.appGlobalAuthKeyHwSignature
      )

      is DdkBackedUp -> RegeneratingTcCertificatesState(
        sealedCsek = recovery.sealedCsek,
        f8eSpendingKeyset = recovery.f8eSpendingKeyset,
        keysetState = if (recovery.keysets.isNotEmpty()) Complete(recovery.keysets) else Incomplete,
        hwSignature = recovery.appGlobalAuthKeyHwSignature
      )

      is BackedUpToCloud -> PerformingSweepState(
        hasAttemptedSweep = false,
        keybox = createNewKeybox(
          recovery = recovery,
          f8eSpendingKeyset = recovery.f8eSpendingKeyset,
          keysetState = if (recovery.keysets.isNotEmpty()) Complete(recovery.keysets) else Incomplete,
          hardwareSpendingKey = recovery.hardwareSpendingKey,
          appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature
        )
      )

      is SweepAttempted -> PerformingSweepState(
        hasAttemptedSweep = true,
        keybox = createNewKeybox(
          recovery = recovery,
          f8eSpendingKeyset = recovery.f8eSpendingKeyset,
          keysetState = if (recovery.keysets.isNotEmpty()) Complete(recovery.keysets) else Incomplete,
          hardwareSpendingKey = recovery.hardwareSpendingKey,
          appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature
        )
      )
    }
  }

  private fun ServerRecovery.remainingDelayPeriod(): Duration =
    nonNegativeDurationBetween(
      startTime = clock.now(),
      endTime = delayEndTime
    )

  private fun getHwProofOfPossessionOrCancelDirectly(
    props: RecoveryInProgressProps,
    rollbackFromAwaitingProofOfPossession: () -> Unit,
  ): State {
    return when (props.recovery.factorToRecover) {
      App -> ResolvingHardwareTypeForCancellationState(rollbackFromAwaitingProofOfPossession)
      Hardware -> CancellingState(CancelLostHardwareRecovery)
    }
  }

  private fun provisionOrPerformDdkBackup(
    sealedCsek: SealedCsek,
    sealedSsek: SealedSsek?,
    f8eSpendingKeyset: F8eSpendingKeyset,
    keysetState: KeysetState,
    sealedDdkResult: SealedData?,
    hwSignature: AppGlobalAuthKeyHwSignature,
  ): State {
    val firmwareVersion = firmwareDataService.firmwareData().value.firmwareDeviceInfo?.version
    val minFirmwareVersion = minFirmwareVersionFeatureFlag.flagValue().value.value

    val shouldProvision = firmwareVersion != null &&
      minFirmwareVersion.isNotEmpty() &&
      semverToInt(firmwareVersion) >= semverToInt(minFirmwareVersion)

    return if (shouldProvision) {
      // W1 provisioning unified into BuildingHardwareDescriptorState
      // (signedKeysResponse = null signals W1 provisioning path)
      BuildingHardwareDescriptorState(
        sealedCsek = sealedCsek,
        sealedSsek = sealedSsek,
        f8eSpendingKeyset = f8eSpendingKeyset,
        keysetState = keysetState,
        signedKeysResponse = null,
        sealedDdkResult = sealedDdkResult
      )
    } else {
      PerformingDdkBackupState(
        sealedCsek = sealedCsek,
        f8eSpendingKeyset = f8eSpendingKeyset,
        keysetState = keysetState,
        delegatedDecryptionKey = null,
        sealedDdkResult = sealedDdkResult,
        hwSignature = hwSignature
      )
    }
  }

  private data class W3ActionProofBindings(
    val descriptorBackupsBindings: String,
    val descriptorBackupsNonce: String,
    val activateKeysetBindings: String,
    val activateKeysetNonce: String,
    val actionProofVersion: UInt,
  )

  /**
   * Prepares W3 action proof bindings for both descriptor-backups and activate-keyset actions.
   * Returns null if not W3 hardware.
   */
  private suspend fun prepareW3Bindings(
    accountId: FullAccountId,
    keysetId: String,
  ): Result<W3ActionProofBindings?, Throwable> {
    if (!isW3Hardware()) return Ok(null)
    val dbNonce = actionProofService.generateNonce()
    val dbBindings = actionProofService.buildBindings(nonce = dbNonce, accountId = accountId)
      .getOrElse { return Err(Error(it)) }
    val akNonce = actionProofService.generateNonce()
    val akBindings = actionProofService.buildBindings(
      extra = mapOf("eid" to keysetId),
      nonce = akNonce,
      accountId = accountId
    ).getOrElse { return Err(Error(it)) }

    return Ok(
      W3ActionProofBindings(
        descriptorBackupsBindings = dbBindings,
        descriptorBackupsNonce = dbNonce,
        activateKeysetBindings = akBindings,
        activateKeysetNonce = akNonce,
        actionProofVersion = ActionProofService.ACTION_PROOF_VERSION
      )
    )
  }

  /**
   * Converts hardware signatures + nonces into dual action proofs with both
   * HW and app signatures (BothFactors).
   * Returns null on failure.
   */
  private suspend fun createW3ActionProofs(
    descriptorBackupsSignature: String,
    descriptorBackupsNonce: String,
    descriptorBackupsBindings: String,
    activateKeysetSignature: String,
    activateKeysetNonce: String,
    activateKeysetBindings: String,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
  ): Pair<PrivilegedActionProof, PrivilegedActionProof>? {
    // App co-sign the same payloads the firmware signed
    val dbAppSig = actionProofService.cosignPayload(
      Action.UPDATE_DESCRIPTOR_BACKUPS, descriptorBackupsBindings, appAuthKey
    ).getOrElse { return null }
    val akAppSig = actionProofService.cosignPayload(
      Action.ROTATE_SPENDING_KEYSET, activateKeysetBindings, appAuthKey
    ).getOrElse { return null }

    val dbHeader = actionProofService.createActionProofHeader(
      listOf(descriptorBackupsSignature, dbAppSig), descriptorBackupsNonce
    ).getOrElse { return null }
    val akHeader = actionProofService.createActionProofHeader(
      listOf(activateKeysetSignature, akAppSig), activateKeysetNonce
    ).getOrElse { return null }
    return HwSignedAction(dbHeader) to HwSignedAction(akHeader)
  }

  private fun isW3Hardware(): Boolean =
    firmwareDataService.firmwareData()
      .value
      .firmwareDeviceInfo
      ?.hardwareType() == HardwareType.W3

  private sealed interface State {
    /**
     * @property [remainingDelayPeriod] remaining amount of time until Delay period finishes.
     */
    data class WaitingForDelayPeriodState(
      val remainingDelayPeriod: Duration,
      val delayPeriodStartTime: Instant,
      val delayPeriodEndTime: Instant,
      val fullAccountId: FullAccountId,
    ) : State

    data object VerifyingNotificationCommsForCancellationState : State

    /**
     * Resolving the hardware type from FirmwareDeviceInfoDao before showing the
     * proof-of-possession UI. Transitions to [AwaitingCancellationProofOfPossessionState].
     */
    data class ResolvingHardwareTypeForCancellationState(
      val rollback: () -> Unit,
    ) : State

    /**
     * This is the first step in performing a cancellation.
     */
    data class AwaitingCancellationProofOfPossessionState(
      val rollback: () -> Unit,
      val hardwareType: HardwareType,
    ) : State

    /**
     * [AwaitingCancellationProofOfPossessionState] failed.
     */
    data class FailedToCancelRecoveryState(
      val cause: Error,
      val isNetworkError: Boolean,
    ) : State

    data object ReadyToCompleteRecoveryState : State

    data class FailedToRotateAuthState(
      val cause: Throwable,
    ) : State

    data class CheckCompletionAttemptForSuccessOrCancellation(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
    ) : State

    data class RotatingAuthTokensState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
    ) : State

    /**
     * Awaiting for hardware to
     *
     * @property csek brand new CSEK to be sealed by hardware. Sealed CSEK will be used to backup
     * keybox after recovery is complete.
     */
    data class AwaitingChallengeAndSeksSignedWithHardwareState(
      val challenge: DelayNotifyChallenge,
      val csek: Csek? = null,
      val ssek: Ssek? = null,
    ) : State

    data class CancellingState(
      val cancellationRequest: DelayNotifyCancellationRequest,
    ) : State

    /**
     * Rotating authentication keys with f8e. See [DelayNotifyService] for
     * details.
     */
    data class RotatingAuthKeysWithF8eState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek,
      val hardwareSignedChallenge: SignedChallenge.HardwareSignedChallenge,
    ) : State

    data class DelegatedDecryptionKeyErrorState(
      val cause: Error,
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val f8eSpendingKeyset: F8eSpendingKeyset,
    ) : State

    data class RemovingTrustedContactsState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val f8eSpendingKeyset: F8eSpendingKeyset,
    ) : State

    data class FailedToCreateSpendingKeysState(
      val cause: Error,
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
    ) : State

    /**
     * Creating new spending keyset on f8e.
     */
    data class CreatingSpendingKeysWithF8eState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
    ) : State

    /**
     * State for activating the spending keyset after creation and descriptor backups
     */
    data class ActivatingSpendingKeysetState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val activateKeysetProof: PrivilegedActionProof,
      val keysetState: KeysetState,
      val sealedDdkResult: SealedData?,
    ) : State

    /**
     * Failure to activate the spending keyset
     */
    data class FailedToActivateSpendingKeysetState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val activateKeysetProof: PrivilegedActionProof,
      val keysetState: KeysetState,
      val cause: Error,
      val sealedDdkResult: SealedData?,
    ) : State

    /**
     * Generating new TC certificates using updated auth keys.
     */
    data class RegeneratingTcCertificatesState(
      val sealedCsek: SealedCsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val keysetState: KeysetState,
      val hwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    data class FailedRegeneratingTcCertificatesState(
      val sealedCsek: SealedCsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val keysetState: KeysetState,
      val cause: Error,
      val hwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    /**
     * Creating and uploading DDK sealed with new Hardware
     */
    data class PerformingDdkBackupState(
      val sealedCsek: SealedCsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val keysetState: KeysetState,
      val delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>?,
      val sealedDdkResult: SealedData?,
      val hwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    data class FailedPerformingDdkBackupState(
      val sealedCsek: SealedCsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val keysetState: KeysetState,
      val cause: Throwable?,
      val delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>?,
      val sealedDdkResult: SealedData?,
      val hwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    /**
     * Creating and uploading backup for new keybox.
     */
    data class PerformingCloudBackupState(
      val sealedCsek: SealedCsek,
      val keybox: Keybox,
    ) : State

    data class FailedPerformingCloudBackupState(
      val cause: Throwable?,
      val sealedCsek: SealedCsek,
      val keybox: Keybox,
    ) : State

    /**
     * Creating and broadcasting sweep transaction to move funds to new keyset.
     */
    data class PerformingSweepState(
      val hasAttemptedSweep: Boolean,
      val keybox: Keybox,
    ) : State

    data class ExitedPerformingSweepState(
      val keybox: Keybox,
    ) : State

    data class FailedToCompleteRecoveryState(
      val keybox: Keybox,
      val cause: Error,
    ) : State

    /**
     * Processing descriptor backups (prepare and encrypt/decrypt) for recovery.
     */
    data class ProcessingDescriptorBackupsState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek,
      val descriptorBackupsProof: PrivilegedActionProof,
      val activateKeysetProof: PrivilegedActionProof,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val sealedDdkResult: SealedData?,
    ) : State

    /**
     * Failed to process descriptor backups.
     */
    data class FailedToProcessDescriptorBackupsState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val cause: Error,
      val descriptorBackupsProof: PrivilegedActionProof,
      val activateKeysetProof: PrivilegedActionProof,
      val sealedDdkResult: SealedData?,
    ) : State

    /**
     * Uploading descriptor backups to F8e.
     */
    data class UploadingDescriptorBackupsState(
      val sealedCsek: SealedCsek,
      val sealedSsekForEncryption: SealedSsek,
      val sealedSsekForDecryption: SealedSsek?,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val descriptorBackupsProof: PrivilegedActionProof,
      val activateKeysetProof: PrivilegedActionProof,
      val descriptorsToDecrypt: List<DescriptorBackup>,
      val keysetsToEncrypt: List<SpendingKeyset>,
      val sealedDdkResult: SealedData?,
    ) : State

    /**
     * Building hardware descriptor via NFC (W3) or provisioning app auth key (W1).
     * When signedKeysResponse is non-null, performs W3 descriptor validation.
     * When signedKeysResponse is null, performs W1 app auth key provisioning.
     */
    data class BuildingHardwareDescriptorState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val keysetState: KeysetState,
      val signedKeysResponse: build.wallet.f8e.recovery.SignedKeysetVerificationResponse?,
      val sealedDdkResult: SealedData?,
    ) : State

    /**
     * Persisting hardware descriptor validation progress before moving to DDK backup.
     */
    data class PersistingHwDescriptorValidationState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val keysetState: KeysetState,
      val sealedDdkResult: SealedData?,
      val hwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    /**
     * Failed to build hardware descriptor.
     */
    data class FailedToBuildHardwareDescriptorState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val keysetState: KeysetState,
      val cause: Error,
      val sealedDdkResult: SealedData?,
    ) : State

    /** Preparing for tap 2: refreshes token, checks TCs, fetches DDK, checks SSEK. */
    data class PreparingProofAndKeyTransferState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val f8eSpendingKeyset: F8eSpendingKeyset,
    ) : State

    /** Tap 2 for Lost App. */
    data class AwaitingProofAndKeyTransferLostAppState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val accessToken: bitkey.auth.AccessToken,
      val sealedDdkData: SealedData?,
      val sealedSsekForDecryption: SealedSsek?,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      // W3 action proof fields (null for W1):
      val descriptorBackupsBindings: String?,
      val descriptorBackupsNonce: String?,
      val activateKeysetBindings: String?,
      val activateKeysetNonce: String?,
      val actionProofVersion: UInt?,
    ) : State

    /** Tap 2 for Lost HW. */
    data class AwaitingProofAndKeyTransferLostHwState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val accessToken: bitkey.auth.AccessToken,
      val ddkKeypair: AppKey<DelegatedDecryptionKey>?,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      // W3 action proof fields (null for W1):
      val descriptorBackupsBindings: String?,
      val descriptorBackupsNonce: String?,
      val activateKeysetBindings: String?,
      val activateKeysetNonce: String?,
      val actionProofVersion: UInt?,
    ) : State
  }
}

private fun Error.isNetworkError(): Boolean {
  return when {
    this !is CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError -> false
    error is F8eError.ConnectivityError -> true
    else -> false
  }
}

private fun CancelDelayNotifyRecoveryError.isNeedsCommsVerificationError(): Boolean {
  return when (this) {
    is CancelDelayNotifyRecoveryError.CommsVerificationRequiredError -> true
    is CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError -> {
      val f8eError =
        error as? F8eError.SpecificClientError<CancelDelayNotifyRecoveryErrorCode>
      f8eError?.errorCode == CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED
    }
    else -> false
  }
}
