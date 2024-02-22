package build.wallet.statemachine.data.recovery.inprogress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AuthProtocolError
import build.wallet.auth.AuthTokenScope
import build.wallet.auth.logAuthFailure
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.csek.CsekGenerator
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.coroutines.delayedResult
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.recovery.ServerRecovery
import build.wallet.nfc.transaction.SignChallengeAndCsek
import build.wallet.notifications.DeviceTokenManager
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.platform.random.Uuid
import build.wallet.recovery.ChallengeToCompleteRecovery
import build.wallet.recovery.LocalRecoveryAttemptProgress
import build.wallet.recovery.LocalRecoveryAttemptProgress.CompletionAttemptFailedDueToServerCancellation
import build.wallet.recovery.LocalRecoveryAttemptProgress.SweptFunds
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.BackedUpToCloud
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.CreatedSpendingKeys
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.MaybeNoLongerRecovering
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.RotatedAuthKeys
import build.wallet.recovery.RecoveryAuthCompleter
import build.wallet.recovery.RecoveryCanceler
import build.wallet.recovery.RecoveryDao
import build.wallet.recovery.RecoverySyncer
import build.wallet.recovery.SignedChallengeToCompleteRecovery
import build.wallet.recovery.socrec.PostSocRecTaskRepository
import build.wallet.recovery.socrec.SocRecRelationshipsRepository
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.AwaitingProofOfPossessionForCancellationData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CancellingData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.AwaitingHardwareProofOfPossessionData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.CreatingSpendingKeysWithF8EData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.FailedToCreateSpendingKeysData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.ExitedPerformingSweepData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.FailedGettingTrustedContactsData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.FailedPerformingCloudBackupData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.GettingTrustedContactsData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.PerformingCloudBackupData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.PerformingSweepData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.AwaitingChallengeAndCsekSignedWithHardwareData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.FailedToRotateAuthData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.ReadyToCompleteRecoveryData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.RotatingAuthKeysWithF8eData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.FailedToCancelRecoveryData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.VerifyingNotificationCommsForCancellationData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.AwaitingCancellationProofOfPossessionState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.AwaitingChallengeAndCsekSignedWithHardwareState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.AwaitingHardwareProofOfPossessionState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.CancellingState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.CheckCompletionAttemptForSuccessOrCancellation
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.CreatingSpendingKeysWithF8eState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.ExitedPerformingSweepState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.FailedPerformingCloudBackupState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.FailedToCancelRecoveryState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.FailedToCreateSpendingKeysState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.FailedToRotateAuthState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.GettingTrustedContactsState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.PerformingCloudBackupState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.PerformingSweepState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.ReadyToCompleteRecoveryState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.RotatingAuthKeysWithF8eState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.VerifyingNotificationCommsForCancellationState
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.WaitingForDelayPeriodState
import build.wallet.statemachine.data.recovery.sweep.SweepDataProps
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachine
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataProps
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachine
import build.wallet.time.nonNegativeDurationBetween
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
 * @param onRetryCloudRecovery
 */
data class RecoveryInProgressProps(
  val keyboxConfig: KeyboxConfig,
  val recovery: StillRecovering,
  val onRetryCloudRecovery: (() -> Unit)?,
) {
  init {
    when (recovery.factorToRecover) {
      // When recovering App factor, we should have an option to go through Cloud Backup recovery
      App -> requireNotNull(onRetryCloudRecovery)
      // Cloud Backup recovery is not relevant when recovering Hardware factor
      Hardware -> require(onRetryCloudRecovery == null)
    }
  }
}

class RecoveryInProgressDataStateMachineImpl(
  private val recoveryCanceler: RecoveryCanceler,
  private val clock: Clock,
  private val csekGenerator: CsekGenerator,
  private val csekDao: CsekDao,
  private val recoveryAuthCompleter: RecoveryAuthCompleter,
  private val sweepDataStateMachine: SweepDataStateMachine,
  private val f8eSpendingKeyRotator: F8eSpendingKeyRotator,
  private val uuid: Uuid,
  private val recoverySyncer: RecoverySyncer,
  private val recoveryNotificationVerificationDataStateMachine:
    RecoveryNotificationVerificationDataStateMachine,
  private val accountAuthenticator: AccountAuthenticator,
  private val recoveryDao: RecoveryDao,
  private val deviceTokenManager: DeviceTokenManager,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val socRecRelationshipsRepository: SocRecRelationshipsRepository,
  private val postSocRecTaskRepository: PostSocRecTaskRepository,
) : RecoveryInProgressDataStateMachine {
  @Composable
  override fun model(props: RecoveryInProgressProps): RecoveryInProgressData {
    var state by remember(props.keyboxConfig, props.recovery) {
      mutableStateOf(
        calculateInitialState(props.keyboxConfig, props.recovery)
      )
    }
    val scope = rememberStableCoroutineScope()
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
          retryCloudRecovery = props.onRetryCloudRecovery,
          cancel = {
            state =
              getHwProofOfPossessionOrCancelDirectly(
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
          startComplete = {
            scope.launch {
              state =
                AwaitingChallengeAndCsekSignedWithHardwareState(
                  challenge =
                    ChallengeToCompleteRecovery(
                      app = props.recovery.appGlobalAuthKey,
                      recovery = props.recovery.appRecoveryAuthKey,
                      hw = props.recovery.hardwareAuthKey
                    ),
                  csek = csekGenerator.generate()
                )
            }
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

      is CheckCompletionAttemptForSuccessOrCancellation -> {
        LaunchedEffect("checking auth") {
          delayedResult(2.seconds) {
            accountAuthenticator
              .appAuth(
                f8eEnvironment = props.keyboxConfig.f8eEnvironment,
                appAuthPublicKey = props.recovery.appGlobalAuthKey
              )
          }
            .logAuthFailure { "Error authenticating with new app auth key after recovery completed." }
            .onSuccess {
              recoveryDao.setLocalRecoveryProgress(
                LocalRecoveryAttemptProgress.RotatedAuthKeys
              )
            }
            .onFailure {
              when (it) {
                is AuthProtocolError -> {
                  recoveryDao.setLocalRecoveryProgress(
                    CompletionAttemptFailedDueToServerCancellation
                  )
                }
                else -> {
                  state = FailedToRotateAuthState
                }
              }
            }
        }
        RotatingAuthKeysWithF8eData(props.recovery.factorToRecover)
      }

      is VerifyingNotificationCommsForCancellationState -> {
        VerifyingNotificationCommsForCancellationData(
          data =
            recoveryNotificationVerificationDataStateMachine.model(
              props =
                RecoveryNotificationVerificationDataProps(
                  f8eEnvironment = props.keyboxConfig.f8eEnvironment,
                  fullAccountId = props.recovery.fullAccountId,
                  onRollback = {
                    // Take them back to the beginning
                    state = calculateInitialState(props.keyboxConfig, props.recovery)
                  },
                  onComplete = {
                    state =
                      getHwProofOfPossessionOrCancelDirectly(
                        props,
                        rollbackFromAwaitingProofOfPossession = {
                          state = calculateInitialState(props.keyboxConfig, props.recovery)
                        }
                      )
                  },
                  hwFactorProofOfPossession = null,
                  lostFactor = props.recovery.factorToRecover
                )
            ),
          lostFactor = props.recovery.factorToRecover
        )
      }

      is CancellingState -> {
        LaunchedEffect("cancelling-recovery") {
          cancelRecovery(
            props,
            dataState.hwFactorProofOfPossession,
            onNeedsCommsVerificationError = {
              state = VerifyingNotificationCommsForCancellationState
            },
            onError = { error ->
              state =
                FailedToCancelRecoveryState(
                  isNetworkError = error.isNetworkError()
                )
            }
          )
        }

        return CancellingData(props.recovery.factorToRecover)
      }

      is AwaitingCancellationProofOfPossessionState -> {
        AwaitingProofOfPossessionForCancellationData(
          appAuthKey = props.recovery.appGlobalAuthKey,
          addHardwareProofOfPossession = {
            state = CancellingState(it)
          },
          rollback = dataState.rollback,
          fullAccountId = props.recovery.fullAccountId
        )
      }

      is AwaitingChallengeAndCsekSignedWithHardwareState -> {
        AwaitingChallengeAndCsekSignedWithHardwareData(
          nfcTransaction =
            SignChallengeAndCsek(
              challenge = dataState.challenge.bytes,
              csek = dataState.csek,
              success = { response ->
                scope.launch {
                  csekDao.set(response.sealedCsek, dataState.csek)
                    .onSuccess { _ ->
                      state =
                        RotatingAuthKeysWithF8eState(
                          sealedCsek = response.sealedCsek,
                          challenge = dataState.challenge,
                          hardwareSignedChallenge =
                            SignedChallengeToCompleteRecovery(
                              signature = response.signedChallenge,
                              signingFactor = Hardware
                            )
                        )
                    }
                    .onFailure {
                      state = FailedToRotateAuthState
                    }
                }
              },
              failure = { state = ReadyToCompleteRecoveryState },
              isHardwareFake = props.keyboxConfig.isHardwareFake
            )
        )
      }

      is FailedToRotateAuthState ->
        FailedToRotateAuthData(
          onConfirm = { state = ReadyToCompleteRecoveryState }
        )

      is RotatingAuthKeysWithF8eState -> {
        LaunchedEffect("rotate-auth-keys") {
          rotateAuthKeys(props, dataState)
            .onFailure {
              state = FailedToRotateAuthState
            }
        }
        RotatingAuthKeysWithF8eData(props.recovery.factorToRecover)
      }

      is AwaitingHardwareProofOfPossessionState ->
        AwaitingHardwareProofOfPossessionData(
          fullAccountId = props.recovery.fullAccountId,
          keyboxConfig = props.keyboxConfig,
          appAuthKey = props.recovery.appGlobalAuthKey,
          addHwFactorProofOfPossession = { hardwareProofOfPossession ->
            state =
              CreatingSpendingKeysWithF8eState(dataState.sealedCsek, hardwareProofOfPossession)
          },
          rollback = {
            state = ReadyToCompleteRecoveryState
          }
        )

      is CreatingSpendingKeysWithF8eState -> {
        LaunchedEffect("create-spending-keys") {
          rotateF8eSpendingKeyToCompleteRecovery(props, dataState)
            .onFailure {
              state =
                FailedToCreateSpendingKeysState(
                  sealedCsek = dataState.sealedCsek,
                  dataState.hardwareProofOfPossession
                )
            }
        }
        CreatingSpendingKeysWithF8EData(props.recovery.factorToRecover)
      }

      is FailedToCreateSpendingKeysState ->
        FailedToCreateSpendingKeysData(
          physicalFactor = props.recovery.factorToRecover,
          onRetry = {
            state =
              CreatingSpendingKeysWithF8eState(
                sealedCsek = dataState.sealedCsek,
                hardwareProofOfPossession = dataState.hardwareProofOfPossession
              )
          }
        )

      is FailedPerformingCloudBackupState ->
        FailedPerformingCloudBackupData(
          physicalFactor = props.recovery.factorToRecover,
          retry = {
            state =
              PerformingCloudBackupState(
                sealedCsek = dataState.sealedCsek,
                keybox = dataState.keybox,
                trustedContacts = dataState.trustedContacts
              )
          }
        )

      is GettingTrustedContactsState -> {
        GetTrustedContactsEffect(
          props = props,
          dataState = dataState,
          assignState = { newState -> state = newState }
        )
        GettingTrustedContactsData
      }

      is State.FailedGettingTrustedContactsState -> {
        FailedGettingTrustedContactsData(
          physicalFactor = props.recovery.factorToRecover,
          retry = {
            state =
              GettingTrustedContactsState(
                sealedCsek = dataState.sealedCsek,
                keybox = dataState.keybox
              )
          }
        )
      }

      is PerformingCloudBackupState ->
        PerformingCloudBackupData(
          sealedCsek = dataState.sealedCsek,
          keybox = dataState.keybox,
          trustedContacts = dataState.trustedContacts,
          onBackupFinished = {
            scope.launch {
              recoverySyncer
                .setLocalRecoveryProgress(LocalRecoveryAttemptProgress.BackedUpToCloud)
            }
          },
          onBackupFailed = {
            state =
              FailedPerformingCloudBackupState(
                sealedCsek = dataState.sealedCsek,
                keybox = dataState.keybox,
                trustedContacts = dataState.trustedContacts
              )
          }
        )

      is PerformingSweepState ->
        PerformingSweepData(
          sweepData =
            sweepDataStateMachine.model(
              props =
                SweepDataProps(
                  recoveredFactor = props.recovery.factorToRecover,
                  keybox = dataState.keybox,
                  onSuccess = {
                    scope.launch {
                      // Set the flag to no longer show the replace hardware card nudge
                      // this flag is used by the MoneyHomeCardsUiStateMachine
                      // and toggled on by the FullAccountCloudBackupRestorationUiStateMachine
                      postSocRecTaskRepository.setHardwareReplacementNeeded(false)
                      recoverySyncer
                        .setLocalRecoveryProgress(
                          SweptFunds(dataState.keybox)
                        )
                    }
                  }
                )
            ),
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
                keybox = dataState.keybox
              )
          }
        )

      is FailedToCancelRecoveryState ->
        FailedToCancelRecoveryData(
          recoveredFactor = props.recovery.factorToRecover,
          isNetworkError = dataState.isNetworkError,
          onAcknowledge = {
            state = ReadyToCompleteRecoveryState
          }
        )
    }
  }

  @Suppress("FunctionName")
  @Composable
  private fun GetTrustedContactsEffect(
    props: RecoveryInProgressProps,
    dataState: GettingTrustedContactsState,
    assignState: (State) -> Unit,
  ) {
    LaunchedEffect("get-trusted-contacts") {
      socRecRelationshipsRepository.syncRelationships(
        accountId = props.recovery.fullAccountId,
        f8eEnvironment = props.keyboxConfig.f8eEnvironment
      ).onSuccess { relationships ->
        assignState(
          PerformingCloudBackupState(
            sealedCsek = dataState.sealedCsek,
            keybox = dataState.keybox,
            trustedContacts = relationships.trustedContacts
          )
        )
      }.onFailure { error ->
        assignState(
          State.FailedGettingTrustedContactsState(
            sealedCsek = dataState.sealedCsek,
            keybox = dataState.keybox,
            error = error
          )
        )
      }
    }
  }

  private fun createNewKeybox(
    keyboxConfig: KeyboxConfig,
    recovery: StillRecovering,
    f8eSpendingKeyset: F8eSpendingKeyset,
  ): Keybox {
    val spendingKeyset =
      SpendingKeyset(
        localId = uuid.random(),
        f8eSpendingKeyset = f8eSpendingKeyset,
        networkType = keyboxConfig.networkType,
        appKey = recovery.appSpendingKey,
        hardwareKey = recovery.hardwareSpendingKey
      )
    return Keybox(
      localId = uuid.random(),
      fullAccountId = recovery.fullAccountId,
      activeSpendingKeyset = spendingKeyset,
      // TODO(W-3070): persist inactive keysets
      inactiveKeysets = emptyImmutableList(),
      activeKeyBundle =
        AppKeyBundle(
          localId = uuid.random(),
          spendingKey = recovery.appSpendingKey,
          authKey = recovery.appGlobalAuthKey,
          networkType = keyboxConfig.networkType,
          recoveryAuthKey = recovery.appRecoveryAuthKey
        ),
      config = keyboxConfig
    )
  }

  /**
   * Calculate initial state based on remaining delay period.
   * If delay period is still pending, return [WaitingForDelayPeriodState].
   * Otherwise, we are ready to complete recovery, return [ReadyToCompleteRecoveryState].
   */
  private fun calculateInitialState(
    keyboxConfig: KeyboxConfig,
    recovery: StillRecovering,
  ): State {
    return when (recovery) {
      is InitiatedRecovery ->
        when (
          val remainingDelayPeriod = recovery.serverRecovery.remainingDelayPeriod()
        ) {
          Duration.ZERO -> ReadyToCompleteRecoveryState
          else ->
            WaitingForDelayPeriodState(
              remainingDelayPeriod = remainingDelayPeriod,
              delayPeriodStartTime = recovery.serverRecovery.delayStartTime,
              delayPeriodEndTime = recovery.serverRecovery.delayEndTime,
              fullAccountId = recovery.fullAccountId
            )
        }
      is MaybeNoLongerRecovering ->
        CheckCompletionAttemptForSuccessOrCancellation(
          recovery.sealedCsek
        )
      is RotatedAuthKeys -> {
        AwaitingHardwareProofOfPossessionState(
          sealedCsek = recovery.sealedCsek
        )
      }
      is CreatedSpendingKeys -> {
        GettingTrustedContactsState(
          sealedCsek = recovery.sealedCsek,
          keybox = createNewKeybox(keyboxConfig, recovery, recovery.f8eSpendingKeyset)
        )
      }

      is BackedUpToCloud -> {
        PerformingSweepState(
          keybox = createNewKeybox(keyboxConfig, recovery, recovery.f8eSpendingKeyset)
        )
      }
    }
  }

  private fun ServerRecovery.remainingDelayPeriod(): Duration =
    nonNegativeDurationBetween(
      startTime = clock.now(),
      endTime = delayEndTime
    )

  private suspend fun cancelRecovery(
    props: RecoveryInProgressProps,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
    onNeedsCommsVerificationError: () -> Unit,
    onError: (RecoveryCanceler.RecoveryCancelerError) -> Unit,
  ): Result<Unit, RecoveryCanceler.RecoveryCancelerError> {
    return recoveryCanceler.cancel(
      f8eEnvironment = props.keyboxConfig.f8eEnvironment,
      fullAccountId = props.recovery.fullAccountId,
      hwFactorProofOfPossession = hwFactorProofOfPossession
    )
      .onFailure { error ->
        if (error.isNeedsCommsVerificationError()) {
          onNeedsCommsVerificationError()
        } else {
          onError(error)
        }
      }
  }

  private fun getHwProofOfPossessionOrCancelDirectly(
    props: RecoveryInProgressProps,
    rollbackFromAwaitingProofOfPossession: () -> Unit,
  ): State {
    return when (props.recovery.factorToRecover) {
      App ->
        AwaitingCancellationProofOfPossessionState(
          rollback = rollbackFromAwaitingProofOfPossession
        )
      Hardware ->
        CancellingState(null)
    }
  }

  private suspend fun rotateAuthKeys(
    props: RecoveryInProgressProps,
    state: RotatingAuthKeysWithF8eState,
  ): Result<Unit, Throwable> =
    recoveryAuthCompleter
      .rotateAuthKeys(
        f8eEnvironment = props.keyboxConfig.f8eEnvironment,
        fullAccountId = props.recovery.fullAccountId,
        challenge = state.challenge,
        hardwareSignedChallenge = state.hardwareSignedChallenge,
        destinationAppGlobalAuthPubKey = props.recovery.appGlobalAuthKey,
        sealedCsek = state.sealedCsek
      )

  private suspend fun rotateF8eSpendingKeyToCompleteRecovery(
    props: RecoveryInProgressProps,
    state: CreatingSpendingKeysWithF8eState,
  ): Result<Unit, Error> =
    binding {
      val f8eSpendingKeyset =
        f8eSpendingKeyRotator
          .rotateSpendingKey(
            keyboxConfig = props.keyboxConfig,
            fullAccountId = props.recovery.fullAccountId,
            appSpendingKey = props.recovery.appSpendingKey,
            hardwareSpendingKey = props.recovery.hardwareSpendingKey,
            appAuthKey = props.recovery.appGlobalAuthKey,
            hardwareProofOfPossession = state.hardwareProofOfPossession
          )
          .bind()

      deviceTokenManager.addDeviceTokenIfPresentForAccount(
        fullAccountId = props.recovery.fullAccountId,
        f8eEnvironment = props.keyboxConfig.f8eEnvironment,
        authTokenScope = AuthTokenScope.Recovery
      )
        .result
        .takeIf {
          // Only bind for Android. iOS can fail silently here.
          deviceInfoProvider.getDeviceInfo().devicePlatform == Android
        }
        ?.bind()

      recoverySyncer
        .setLocalRecoveryProgress(
          LocalRecoveryAttemptProgress.RotatedSpendingKeys(f8eSpendingKeyset)
        )
        .bind()
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
      val isNetworkError: Boolean,
    ) : State

    data object ReadyToCompleteRecoveryState : State

    data object FailedToRotateAuthState : State

    data class CheckCompletionAttemptForSuccessOrCancellation(val sealedCsek: SealedCsek) : State

    /**
     * Awaiting for hardware to
     *
     * @property csek brand new CSEK to be sealed by hardware. Sealed CSEK will be used to backup
     * keybox after recovery is complete.
     */
    data class AwaitingChallengeAndCsekSignedWithHardwareState(
      val challenge: ChallengeToCompleteRecovery,
      val csek: Csek,
    ) : State

    data class CancellingState(
      val hwFactorProofOfPossession: HwFactorProofOfPossession?,
    ) : State

    /**
     * Rotating authentication keys with f8e. See [RecoveryAuthCompleter] for
     * details.
     */
    data class RotatingAuthKeysWithF8eState(
      val sealedCsek: SealedCsek,
      val challenge: ChallengeToCompleteRecovery,
      val hardwareSignedChallenge: SignedChallengeToCompleteRecovery,
    ) : State {
      init {
        require(hardwareSignedChallenge.signingFactor == Hardware)
      }
    }

    data class FailedToCreateSpendingKeysState(
      val sealedCsek: SealedCsek,
      val hardwareProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * Awaiting for hardware to provide hardware proof of possession.
     */
    data class AwaitingHardwareProofOfPossessionState(
      val sealedCsek: SealedCsek,
    ) : State

    /**
     * Creating new spending keyset on f8e.
     */
    data class CreatingSpendingKeysWithF8eState(
      val sealedCsek: SealedCsek,
      val hardwareProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * Getting [TrustedContact]s.
     */
    data class GettingTrustedContactsState(
      val sealedCsek: SealedCsek,
      val keybox: Keybox,
    ) : State

    data class FailedGettingTrustedContactsState(
      val sealedCsek: SealedCsek,
      val keybox: Keybox,
      val error: Error,
    ) : State

    /**
     * Creating and uploading backup for new keybox.
     */
    data class PerformingCloudBackupState(
      val sealedCsek: SealedCsek,
      val keybox: Keybox,
      val trustedContacts: List<TrustedContact>,
    ) : State

    data class FailedPerformingCloudBackupState(
      val sealedCsek: SealedCsek,
      val keybox: Keybox,
      val trustedContacts: List<TrustedContact>,
    ) : State

    /**
     * Creating and broadcasting sweep transaction to move funds to new keyset.
     */
    data class PerformingSweepState(
      val keybox: Keybox,
    ) : State

    data class ExitedPerformingSweepState(
      val keybox: Keybox,
    ) : State
  }
}

private fun RecoveryCanceler.RecoveryCancelerError.isNetworkError(): Boolean {
  return when {
    this !is RecoveryCanceler.RecoveryCancelerError.F8eCancelDelayNotifyError -> false
    error is F8eError.ConnectivityError -> true
    else -> false
  }
}

private fun RecoveryCanceler.RecoveryCancelerError.isNeedsCommsVerificationError(): Boolean {
  if (this !is RecoveryCanceler.RecoveryCancelerError.F8eCancelDelayNotifyError) {
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
