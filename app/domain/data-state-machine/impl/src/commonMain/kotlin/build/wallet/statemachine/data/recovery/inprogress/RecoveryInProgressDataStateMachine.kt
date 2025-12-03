@file:Suppress("CyclomaticComplexMethod")

package build.wallet.statemachine.data.recovery.inprogress

import androidx.compose.runtime.*
import bitkey.account.*
import bitkey.backup.DescriptorBackup
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.recovery.*
import bitkey.recovery.DelayNotifyCancellationRequest.CancelLostAppAndCloudRecovery
import bitkey.recovery.DelayNotifyCancellationRequest.CancelLostHardwareRecovery
import build.wallet.auth.AuthProtocolError
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.challange.DelayNotifyChallenge
import build.wallet.bitkey.challange.SignedChallenge
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.*
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SealedData
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.ServerRecovery
import build.wallet.feature.flags.EncryptedDescriptorBackupsFeatureFlag
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.feature.isEnabled
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
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.inprogress.KeysetState.Complete
import build.wallet.statemachine.data.recovery.inprogress.KeysetState.Incomplete
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.ProcessingDescriptorBackupsData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.*
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.nonNegativeDurationBetween
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.andThenRecover
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Data state machine for DN Lost App or Hw recovery which is currently in progress or
 * is completing.
 *
 * Covers:
 * - Recovery delay period
 * - Recovery cancellation
 * - Recovery completion
 */
interface RecoveryInProgressDataStateMachine :
  StateMachine<RecoveryInProgressProps, RecoveryInProgressData>

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

data class RecoveryInProgressProps(
  val recovery: StillRecovering,
  val oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
)

@Suppress("LargeClass")
@BitkeyInject(AppScope::class)
class RecoveryInProgressDataStateMachineImpl(
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
  private val encryptedDescriptorBackupsFeatureFlag: EncryptedDescriptorBackupsFeatureFlag,
  private val provisionAppAuthKeyTransactionProvider: ProvisionAppAuthKeyTransactionProvider,
  private val minFirmwareVersionFeatureFlag: FingerprintResetMinFirmwareVersionFeatureFlag,
  private val firmwareDataService: FirmwareDataService,
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
        val firmwareData by remember { firmwareDataService.firmwareData() }.collectAsState()
        val firmwareVersion = firmwareData.firmwareDeviceInfo?.version
        val minFirmwareVersion = minFirmwareVersionFeatureFlag.flagValue().value.value

        // Check if we should skip provisioning based on firmware version
        val shouldSkipProvisioning = firmwareVersion == null ||
          minFirmwareVersion.isEmpty() ||
          semverToInt(firmwareVersion) < semverToInt(minFirmwareVersion)

        LaunchedEffect("rotate-auth-tokens") {
          delayNotifyService
            .rotateAuthTokens()
            .onSuccess {
              state = if (shouldSkipProvisioning) {
                FetchingSealedDelegatedDecryptionKeyFromF8eState(
                  sealedCsek = dataState.sealedCsek,
                  sealedSsek = dataState.sealedSsek
                )
              } else {
                ProvisioningAppAuthKeyToHardwareState(
                  sealedCsek = dataState.sealedCsek,
                  sealedSsek = dataState.sealedSsek,
                  appGlobalAuthKey = props.recovery.appGlobalAuthKey
                )
              }
            }
            .onFailure { error ->
              state = FailedToRotateAuthState(cause = error)
            }
        }
        RotatingAuthKeysWithF8eData(props.recovery.factorToRecover)
      }

      is ProvisioningAppAuthKeyToHardwareState -> {
        ProvisioningAppAuthKeyToHardwareData(
          nfcTransaction = provisionAppAuthKeyTransactionProvider(
            appGlobalAuthPublicKey = dataState.appGlobalAuthKey,
            onSuccess = {
              state = FetchingSealedDelegatedDecryptionKeyFromF8eState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek
              )
            },
            onCancel = {
              state = FailedToRotateAuthState(
                cause = Error("Cancelled provisioning app auth key to hardware")
              )
            }
          )
        )
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
              state = if (error is CancelDelayNotifyRecoveryError && error.isNeedsCommsVerificationError()) {
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

      is AwaitingCancellationProofOfPossessionState -> {
        AwaitingProofOfPossessionForCancellationData(
          appAuthKey = props.recovery.appGlobalAuthKey,
          addHardwareProofOfPossession = {
            state = CancellingState(CancelLostAppAndCloudRecovery(it))
          },
          rollback = dataState.rollback,
          fullAccountId = props.recovery.fullAccountId
        )
      }

      is AwaitingChallengeAndSeksSignedWithHardwareState -> {
        AwaitingChallengeAndCsekSignedWithHardwareData(
          nfcTransaction = SignChallengeAndSealSeks(
            challenge = dataState.challenge,
            success = { response ->
              coroutineBinding {
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
                sealedSsek = dataState.sealedSsek
              )
          },
          onRetry = {
            state = FetchingSealedDelegatedDecryptionKeyFromF8eState(
              sealedCsek = dataState.sealedCsek,
              sealedSsek = dataState.sealedSsek
            )
          }
        )
      }

      is FetchingSealedDelegatedDecryptionKeyFromF8eState -> {
        val isRecoveringApp = props.recovery.factorToRecover == App
        if (!isRecoveringApp) {
          state = AwaitingHardwareProofOfPossessionState(
            sealedCsek = dataState.sealedCsek,
            sealedSsek = dataState.sealedSsek
          )
        } else {
          LaunchedEffect("fetch-ddk-from-f8e") {
            coroutineBinding {
              val relationships = relationshipsService.getRelationshipsWithoutSyncing(
                accountId = props.recovery.fullAccountId
              ).bind()
              // If we don't have any existing endorsed relationships, we can proceed with the recovery
              if (relationships.protectedCustomers.isEmpty() && relationships.endorsedTrustedContacts.isEmpty()) {
                state = AwaitingHardwareProofOfPossessionState(
                  sealedCsek = dataState.sealedCsek,
                  sealedSsek = dataState.sealedSsek
                )
              }

              // if we do have relationships, we need to fetch the DDK
              delegatedDecryptionKeyService.getSealedDelegatedDecryptionKeyData(
                accountId = props.recovery.fullAccountId
              ).bind()
            }.onSuccess { sealedDelegatedDecryptionKeyData ->
              state = FetchingSealedDelegatedDecryptionKeyDataState(
                sealedData = sealedDelegatedDecryptionKeyData,
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek
              )
            }
              .onFailure { error ->
                // If we get a 404, we don't have a DDK stored in F8e and should remove TCs
                state =
                  if (error is HttpError.ClientError && error.response.status.value == 404) {
                    RemovingTrustedContactsState(
                      sealedCsek = dataState.sealedCsek,
                      sealedSsek = dataState.sealedSsek
                    )
                  } else {
                    DelegatedDecryptionKeyErrorState(
                      cause = Error(error),
                      sealedCsek = dataState.sealedCsek,
                      sealedSsek = dataState.sealedSsek
                    )
                  }
              }
          }
        }

        FetchingSealedDelegatedDecryptionKeyFromF8eData(
          physicalFactor = props.recovery.factorToRecover
        )
      }

      is FetchingSealedDelegatedDecryptionKeyDataState -> {
        FetchingSealedDelegatedDecryptionKeyStringData(
          nfcTransaction = UnsealData(
            sealedData = dataState.sealedData,
            success = { result ->
              delegatedDecryptionKeyService
                .restoreDelegatedDecryptionKey(result.unsealedData)
                .onSuccess {
                  state = AwaitingHardwareProofOfPossessionState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsek
                  )
                }
                .onFailure {
                  state = DelegatedDecryptionKeyErrorState(
                    cause = Error(it),
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsek
                  )
                }
            },
            failure = {
              state =
                DelegatedDecryptionKeyErrorState(
                  cause = Error("NFC Error"),
                  sealedCsek = dataState.sealedCsek,
                  sealedSsek = dataState.sealedSsek
                )
            }
          )
        )
      }

      is RemovingTrustedContactsState -> {
        LaunchedEffect("remove-trusted-contacts") {
          delayNotifyService.removeTrustedContacts()
            .onSuccess {
              state =
                AwaitingHardwareProofOfPossessionState(dataState.sealedCsek, dataState.sealedSsek)
            }
            .onFailure {
              state =
                AwaitingHardwareProofOfPossessionState(dataState.sealedCsek, dataState.sealedSsek)
            }
        }

        RemovingTrustedContactsData(
          physicalFactor = props.recovery.factorToRecover
        )
      }

      is AwaitingHardwareProofOfPossessionState -> {
        AwaitingHardwareProofOfPossessionData(
          fullAccountId = props.recovery.fullAccountId,
          appAuthKey = props.recovery.appGlobalAuthKey,
          addHwFactorProofOfPossession = { hardwareProofOfPossession ->
            state =
              CreatingSpendingKeysWithF8eState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek,
                hardwareProofOfPossession = hardwareProofOfPossession
              )
          },
          rollback = {
            state = ReadyToCompleteRecoveryState
          }
        )
      }

      is CreatingSpendingKeysWithF8eState -> {
        LaunchedEffect("create-spending-keys") {
          delayNotifyService.createSpendingKeyset(
            hardwareProofOfPossession = dataState.hardwareProofOfPossession
          )
            .onSuccess { f8eSpendingKeyset ->
              state =
                if (encryptedDescriptorBackupsFeatureFlag.isEnabled() && dataState.sealedSsek != null) {
                  ProcessingDescriptorBackupsState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsek,
                    f8eSpendingKeyset = f8eSpendingKeyset,
                    hardwareProofOfPossession = dataState.hardwareProofOfPossession
                  )
                } else {
                  ActivatingSpendingKeysetState(
                    sealedCsek = dataState.sealedCsek,
                    f8eSpendingKeyset = f8eSpendingKeyset,
                    hardwareProofOfPossession = dataState.hardwareProofOfPossession,
                    keysetState = Incomplete
                  )
                }
            }
            .onFailure { error ->
              state = FailedToCreateSpendingKeysState(
                cause = error,
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsek,
                hardwareProofOfPossession = dataState.hardwareProofOfPossession
              )
            }
        }
        CreatingSpendingKeysWithF8EData(props.recovery.factorToRecover)
      }

      is AwaitingHardwareProofOfPossessionForDescriptorBackupsState -> {
        AwaitingHardwareProofOfPossessionData(
          fullAccountId = props.recovery.fullAccountId,
          appAuthKey = props.recovery.appGlobalAuthKey,
          addHwFactorProofOfPossession = { hardwareProofOfPossession ->
            state = ProcessingDescriptorBackupsState(
              sealedCsek = dataState.sealedCsek,
              sealedSsek = dataState.sealedSsek,
              f8eSpendingKeyset = dataState.f8eSpendingKeyset,
              hardwareProofOfPossession = hardwareProofOfPossession
            )
          },
          rollback = {
            state = calculateInitialState(props.recovery)
          }
        )
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
                    hardwareProofOfPossession = dataState.hardwareProofOfPossession,
                    descriptorsToDecrypt = preparedData.descriptorsToDecrypt,
                    keysetsToEncrypt = preparedData.keysetsToEncrypt
                  )
                }
                is DescriptorBackupPreparedData.NeedsUnsealed -> {
                  state = AwaitingSsekUnsealingState(
                    sealedCsek = dataState.sealedCsek,
                    descriptorsToDecrypt = preparedData.descriptorsToDecrypt,
                    keysetsToEncrypt = preparedData.keysetsToEncrypt,
                    sealedSsekForDecryption = preparedData.sealedSsek,
                    sealedSsekForRecovery = dataState.sealedSsek,
                    hardwareProofOfPossession = dataState.hardwareProofOfPossession,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset
                  )
                }
                is DescriptorBackupPreparedData.EncryptOnly -> {
                  state = UploadingDescriptorBackupsState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsekForEncryption = dataState.sealedSsek,
                    sealedSsekForDecryption = null,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    hardwareProofOfPossession = dataState.hardwareProofOfPossession,
                    descriptorsToDecrypt = emptyList(),
                    keysetsToEncrypt = preparedData.keysetsToEncrypt
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
                hardwareProofOfPossession = dataState.hardwareProofOfPossession
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
            hardwareProofOfPossession = dataState.hardwareProofOfPossession,
            f8eSpendingKeyset = dataState.f8eSpendingKeyset
          )
        }
      )

      is ActivatingSpendingKeysetState -> {
        LaunchedEffect("activate-spending-keyset") {
          delayNotifyService.activateSpendingKeyset(
            keyset = dataState.f8eSpendingKeyset,
            hardwareProofOfPossession = dataState.hardwareProofOfPossession
          )
            .onSuccess {
              state = PerformingDdkBackupState(
                sealedCsek = dataState.sealedCsek,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                keysetState = dataState.keysetState,
                delegatedDecryptionKey = null
              )
            }
            .onFailure { error ->
              state = FailedToActivateSpendingKeysetState(
                sealedCsek = dataState.sealedCsek,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                hardwareProofOfPossession = dataState.hardwareProofOfPossession,
                keysetState = dataState.keysetState,
                cause = Error(error)
              )
            }
        }
        ActivatingSpendingKeysetData(props.recovery.factorToRecover)
      }

      is AwaitingHardwareProofOfPossessionForActivationState -> AwaitingHardwareProofOfPossessionForActivationData(
        physicalFactor = props.recovery.factorToRecover,
        addHardwareProofOfPossession = { hwProofOfPossession ->
          state = ActivatingSpendingKeysetState(
            sealedCsek = dataState.sealedCsek,
            f8eSpendingKeyset = dataState.f8eSpendingKeyset,
            hardwareProofOfPossession = hwProofOfPossession,
            keysetState = dataState.keysetState
          )
        },
        rollback = {
          state = calculateInitialState(props.recovery)
        },
        appAuthKey = props.recovery.appGlobalAuthKey,
        fullAccountId = props.recovery.fullAccountId
      )

      is FailedToActivateSpendingKeysetState -> FailedToActivateSpendingKeysetData(
        physicalFactor = props.recovery.factorToRecover,
        cause = dataState.cause,
        onRetry = {
          state = ActivatingSpendingKeysetState(
            sealedCsek = dataState.sealedCsek,
            f8eSpendingKeyset = dataState.f8eSpendingKeyset,
            hardwareProofOfPossession = dataState.hardwareProofOfPossession,
            keysetState = dataState.keysetState
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
              sealedSsek = dataState.sealedSsek,
              hardwareProofOfPossession = dataState.hardwareProofOfPossession
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
              keysetState = dataState.keysetState
            )
          }
        )
      }

      is PerformingDdkBackupState -> {
        if (props.recovery.factorToRecover != Hardware) {
          LaunchedEffect("set-recovery-progress-ddk-backed-up") {
            // If we're not doing a hardware recovery, we don't need
            // to reseal+upload the DDK, so we can mark as complete
            recoveryStatusService.setLocalRecoveryProgress(LocalRecoveryAttemptProgress.DdkBackedUp)
              .onSuccess {
                state = RegeneratingTcCertificatesState(
                  sealedCsek = dataState.sealedCsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                  keysetState = dataState.keysetState
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
                  delegatedDecryptionKey = keypair
                )
              }
              .onFailure { error ->
                state = FailedPerformingDdkBackupState(
                  sealedCsek = dataState.sealedCsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                  keysetState = dataState.keysetState,
                  cause = error
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
                    keysetState = dataState.keysetState
                  )
                }
                  .onFailure {
                    state = FailedPerformingDdkBackupState(
                      sealedCsek = dataState.sealedCsek,
                      f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                      keysetState = dataState.keysetState,
                      cause = it,
                      delegatedDecryptionKey = dataState.delegatedDecryptionKey
                    )
                  }
              },
              failure = {
                state = FailedPerformingDdkBackupState(
                  sealedCsek = dataState.sealedCsek,
                  f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                  keysetState = dataState.keysetState,
                  cause = Error("NFC Error"),
                  delegatedDecryptionKey = dataState.delegatedDecryptionKey
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
              delegatedDecryptionKey = dataState.delegatedDecryptionKey
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
            keysetState = dataState.keysetState
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
                keysetState = dataState.keysetState
              )
            }
        }
        RegeneratingTcCertificatesData
      }

      is AwaitingSsekUnsealingState -> {
        AwaitingSsekUnsealingData(
          physicalFactor = props.recovery.factorToRecover,
          nfcTransaction = UnsealSsek(
            sealedSsek = dataState.sealedSsekForDecryption,
            success = { unsealedSsek ->
              // Store the unsealed CSEK and proceed with completion
              ssekDao.set(dataState.sealedSsekForDecryption, unsealedSsek)
                .onSuccess {
                  state = UploadingDescriptorBackupsState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsekForEncryption = dataState.sealedSsekForRecovery,
                    sealedSsekForDecryption = dataState.sealedSsekForDecryption,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    hardwareProofOfPossession = dataState.hardwareProofOfPossession,
                    descriptorsToDecrypt = dataState.descriptorsToDecrypt,
                    keysetsToEncrypt = dataState.keysetsToEncrypt
                  )
                }
                .onFailure { error ->
                  state = FailedToProcessDescriptorBackupsState(
                    sealedCsek = dataState.sealedCsek,
                    sealedSsek = dataState.sealedSsekForRecovery,
                    f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                    cause = Error(error),
                    hardwareProofOfPossession = dataState.hardwareProofOfPossession
                  )
                }
            },
            failure = {
              state = FailedToProcessDescriptorBackupsState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsekForRecovery,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                cause = Error("Failed to unseal Ssek via NFC"),
                hardwareProofOfPossession = dataState.hardwareProofOfPossession
              )
            }
          )
        )
      }

      is UploadingDescriptorBackupsState -> {
        LaunchedEffect("upload-descriptor-backups") {
          coroutineBinding {
            val keysets = descriptorBackupService.uploadDescriptorBackups(
              accountId = props.recovery.fullAccountId,
              sealedSsekForEncryption = dataState.sealedSsekForEncryption,
              sealedSsekForDecryption = dataState.sealedSsekForDecryption,
              appAuthKey = props.recovery.appGlobalAuthKey,
              hwKeyProof = dataState.hardwareProofOfPossession,
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
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                hardwareProofOfPossession = dataState.hardwareProofOfPossession,
                keysetState = Complete(keysets)
              )
            }
            .onFailure { error ->
              state = FailedToProcessDescriptorBackupsState(
                sealedCsek = dataState.sealedCsek,
                sealedSsek = dataState.sealedSsekForEncryption,
                f8eSpendingKeyset = dataState.f8eSpendingKeyset,
                cause = error,
                hardwareProofOfPossession = dataState.hardwareProofOfPossession
              )
            }
        }

        UploadingDescriptorBackupsData(props.recovery.factorToRecover)
      }
    }
  }

  private fun createNewKeybox(
    recovery: StillRecovering,
    f8eSpendingKeyset: F8eSpendingKeyset,
    keysetState: KeysetState,
  ): Keybox {
    val accountConfig = when (val config = accountConfigService.activeOrDefaultConfig().value) {
      is DefaultAccountConfig -> config.toFullAccountConfig()
      is FullAccountConfig -> config
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
          hardwareKey = recovery.hardwareSpendingKey
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
      appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
      activeAppKeyBundle = AppKeyBundle(
        localId = uuidGenerator.random(),
        spendingKey = recovery.appSpendingKey,
        authKey = recovery.appGlobalAuthKey,
        networkType = accountConfig.bitcoinNetworkType,
        recoveryAuthKey = recovery.appRecoveryAuthKey
      ),
      activeHwKeyBundle = HwKeyBundle(
        localId = uuidGenerator.random(),
        spendingKey = recovery.hardwareSpendingKey,
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
      is InitiatedRecovery -> when (val remainingDelayPeriod = recovery.serverRecovery.remainingDelayPeriod()) {
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
        val firmwareData = firmwareDataService.firmwareData().value
        val firmwareVersion = firmwareData.firmwareDeviceInfo?.version
        val minFirmwareVersion = minFirmwareVersionFeatureFlag.flagValue().value.value

        // Check if we should skip provisioning based on firmware version
        val shouldSkipProvisioning = firmwareVersion == null ||
          minFirmwareVersion.isEmpty() ||
          semverToInt(firmwareVersion) < semverToInt(minFirmwareVersion)

        if (shouldSkipProvisioning) {
          FetchingSealedDelegatedDecryptionKeyFromF8eState(
            sealedCsek = recovery.sealedCsek,
            sealedSsek = recovery.sealedSsek
          )
        } else {
          ProvisioningAppAuthKeyToHardwareState(
            sealedCsek = recovery.sealedCsek,
            sealedSsek = recovery.sealedSsek,
            appGlobalAuthKey = recovery.appGlobalAuthKey
          )
        }
      }

      is CreatedSpendingKeys -> if (encryptedDescriptorBackupsFeatureFlag.isEnabled() && recovery.sealedSsek != null) {
        AwaitingHardwareProofOfPossessionForDescriptorBackupsState(
          sealedCsek = recovery.sealedCsek,
          sealedSsek = recovery.sealedSsek!!,
          f8eSpendingKeyset = recovery.f8eSpendingKeyset
        )
      } else {
        // Feature flag is disabled or sealedSsek is null (app update during recovery)
        // Since we don't have the hardware proof of possession stored in recovery state,
        // we need to get it again
        AwaitingHardwareProofOfPossessionForActivationState(
          sealedCsek = recovery.sealedCsek,
          f8eSpendingKeyset = recovery.f8eSpendingKeyset,
          keysetState = Incomplete
        )
      }

      is UploadedDescriptorBackups -> AwaitingHardwareProofOfPossessionForActivationState(
        sealedCsek = recovery.sealedCsek,
        f8eSpendingKeyset = recovery.f8eSpendingKeyset,
        keysetState = Complete(recovery.keysets)
      )

      is ActivatedSpendingKeys -> PerformingDdkBackupState(
        sealedCsek = recovery.sealedCsek,
        f8eSpendingKeyset = recovery.f8eSpendingKeyset,
        keysetState = if (recovery.keysets.isNotEmpty()) Complete(recovery.keysets) else Incomplete,
        delegatedDecryptionKey = null
      )

      is DdkBackedUp -> RegeneratingTcCertificatesState(
        sealedCsek = recovery.sealedCsek,
        f8eSpendingKeyset = recovery.f8eSpendingKeyset,
        keysetState = if (recovery.keysets.isNotEmpty()) Complete(recovery.keysets) else Incomplete
      )

      is BackedUpToCloud -> PerformingSweepState(
        hasAttemptedSweep = false,
        keybox = createNewKeybox(
          recovery = recovery,
          f8eSpendingKeyset = recovery.f8eSpendingKeyset,
          keysetState = if (recovery.keysets.isNotEmpty()) Complete(recovery.keysets) else Incomplete
        )
      )

      is SweepAttempted -> PerformingSweepState(
        hasAttemptedSweep = true,
        keybox = createNewKeybox(
          recovery = recovery,
          f8eSpendingKeyset = recovery.f8eSpendingKeyset,
          keysetState = if (recovery.keysets.isNotEmpty()) Complete(recovery.keysets) else Incomplete
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
      App -> AwaitingCancellationProofOfPossessionState(rollbackFromAwaitingProofOfPossession)
      Hardware -> CancellingState(CancelLostHardwareRecovery)
    }
  }

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
     * This is the first step in performing a cancellation.
     */
    data class AwaitingCancellationProofOfPossessionState(
      val rollback: () -> Unit,
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
      val sealedSsek: SealedSsek? = null,
    ) : State

    /**
     * Awaiting for hardware to
     *
     * @property csek brand new CSEK to be sealed by hardware. Sealed CSEK will be used to backup
     * keybox after recovery is complete.
     */
    data class AwaitingChallengeAndSeksSignedWithHardwareState(
      val challenge: DelayNotifyChallenge,
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

    /**
     * Provisioning the new app auth key to hardware via NFC.
     */
    data class ProvisioningAppAuthKeyToHardwareState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val appGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    ) : State

    data class FetchingSealedDelegatedDecryptionKeyFromF8eState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
    ) : State

    data class FetchingSealedDelegatedDecryptionKeyDataState(
      val sealedData: SealedData,
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
    ) : State

    data class DelegatedDecryptionKeyErrorState(
      val cause: Error,
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
    ) : State

    data class RemovingTrustedContactsState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
    ) : State

    data class FailedToCreateSpendingKeysState(
      val cause: Error,
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val hardwareProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * Awaiting for hardware to provide hardware proof of possession.
     */
    data class AwaitingHardwareProofOfPossessionState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
    ) : State

    /**
     * Creating new spending keyset on f8e.
     */
    data class CreatingSpendingKeysWithF8eState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek?,
      val hardwareProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * State for activating the spending keyset after creation and descriptor backups
     */
    data class ActivatingSpendingKeysetState(
      val sealedCsek: SealedCsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val hardwareProofOfPossession: HwFactorProofOfPossession,
      val keysetState: KeysetState,
    ) : State

    /**
     * Awaiting hardware proof of possession to activate the spending keyset.
     */
    data class AwaitingHardwareProofOfPossessionForActivationState(
      val sealedCsek: SealedCsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val keysetState: KeysetState,
    ) : State

    /**
     * Failure to activate the spending keyset
     */
    data class FailedToActivateSpendingKeysetState(
      val sealedCsek: SealedCsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val hardwareProofOfPossession: HwFactorProofOfPossession,
      val keysetState: KeysetState,
      val cause: Error,
    ) : State

    /**
     * Generating new TC certificates using updated auth keys.
     */
    data class RegeneratingTcCertificatesState(
      val sealedCsek: SealedCsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val keysetState: KeysetState,
    ) : State

    data class FailedRegeneratingTcCertificatesState(
      val sealedCsek: SealedCsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val keysetState: KeysetState,
      val cause: Error,
    ) : State

    /**
     * Creating and uploading DDK sealed with new Hardware
     */
    data class PerformingDdkBackupState(
      val sealedCsek: SealedCsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val keysetState: KeysetState,
      val delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>? = null,
    ) : State

    data class FailedPerformingDdkBackupState(
      val sealedCsek: SealedCsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val keysetState: KeysetState,
      val cause: Throwable?,
      val delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>? = null,
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

    /**
     * Awaiting hardware proof of possession before processing descriptor backups.
     */
    data class AwaitingHardwareProofOfPossessionForDescriptorBackupsState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
    ) : State

    /**
     * Processing descriptor backups (prepare and encrypt/decrypt) for recovery.
     */
    data class ProcessingDescriptorBackupsState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek,
      val hardwareProofOfPossession: HwFactorProofOfPossession,
      val f8eSpendingKeyset: F8eSpendingKeyset,
    ) : State

    /**
     * Awaiting hardware to unseal a CSEK for decryption via NFC.
     */
    data class AwaitingSsekUnsealingState(
      val sealedCsek: SealedCsek,
      val descriptorsToDecrypt: List<DescriptorBackup>,
      val keysetsToEncrypt: List<SpendingKeyset>,
      val sealedSsekForDecryption: SealedSsek,
      val sealedSsekForRecovery: SealedSsek,
      val hardwareProofOfPossession: HwFactorProofOfPossession,
      val f8eSpendingKeyset: F8eSpendingKeyset,
    ) : State

    /**
     * Failed to process descriptor backups.
     */
    data class FailedToProcessDescriptorBackupsState(
      val sealedCsek: SealedCsek,
      val sealedSsek: SealedSsek,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val cause: Error,
      val hardwareProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * Uploading descriptor backups to F8e.
     */
    data class UploadingDescriptorBackupsState(
      val sealedCsek: SealedCsek,
      val sealedSsekForEncryption: SealedSsek,
      val sealedSsekForDecryption: SealedSsek?,
      val f8eSpendingKeyset: F8eSpendingKeyset,
      val hardwareProofOfPossession: HwFactorProofOfPossession,
      val descriptorsToDecrypt: List<DescriptorBackup>,
      val keysetsToEncrypt: List<SpendingKeyset>,
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
  if (this !is CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError) {
    return false
  }

  if (error !is F8eError.SpecificClientError) {
    return false
  }

  val f8eError = error as F8eError.SpecificClientError<CancelDelayNotifyRecoveryErrorCode>
  return when (f8eError.errorCode) {
    CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED -> true
    else -> false
  }
}
