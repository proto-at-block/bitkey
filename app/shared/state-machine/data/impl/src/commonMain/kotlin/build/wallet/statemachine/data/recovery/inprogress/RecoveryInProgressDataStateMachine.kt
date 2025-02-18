@file:Suppress("CyclomaticComplexMethod")

package build.wallet.statemachine.data.recovery.inprogress

import androidx.compose.runtime.*
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AuthProtocolError
import build.wallet.auth.AuthTokenScope
import build.wallet.auth.logAuthFailure
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.app.AppAuthPublicKeys
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
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.csek.CsekGenerator
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SealedData
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.recovery.ServerRecovery
import build.wallet.ktor.result.HttpError
import build.wallet.logging.logFailure
import build.wallet.nfc.transaction.SealDelegatedDecryptionKey
import build.wallet.nfc.transaction.SignChallengeAndCsek
import build.wallet.nfc.transaction.UnsealData
import build.wallet.notifications.DeviceTokenManager
import build.wallet.platform.random.UuidGenerator
import build.wallet.recovery.*
import build.wallet.recovery.LocalRecoveryAttemptProgress.CompletionAttemptFailedDueToServerCancellation
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.*
import build.wallet.relationships.DelegatedDecryptionKeyService
import build.wallet.relationships.EndorseTrustedContactsService
import build.wallet.relationships.RelationshipsKeysRepository
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.CancellationRequest.CancelLostAppAndCloudRecovery
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.CancellationRequest.CancelLostHardwareRecovery
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachineImpl.State.*
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.nonNegativeDurationBetween
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * @param onRetryCloudRecovery
 */
data class RecoveryInProgressProps(
  val fullAccountConfig: FullAccountConfig,
  val recovery: StillRecovering,
  val oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
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

@Suppress("LargeClass")
@BitkeyInject(AppScope::class)
class RecoveryInProgressDataStateMachineImpl(
  private val lostHardwareRecoveryService: LostHardwareRecoveryService,
  private val lostAppAndCloudRecoveryService: LostAppAndCloudRecoveryService,
  private val clock: Clock,
  private val csekGenerator: CsekGenerator,
  private val csekDao: CsekDao,
  private val recoveryAuthCompleter: RecoveryAuthCompleter,
  private val f8eSpendingKeyRotator: F8eSpendingKeyRotator,
  private val uuidGenerator: UuidGenerator,
  private val recoverySyncer: RecoverySyncer,
  private val accountAuthenticator: AccountAuthenticator,
  private val recoveryDao: RecoveryDao,
  private val deviceTokenManager: DeviceTokenManager,
  private val delegatedDecryptionKeyService: DelegatedDecryptionKeyService,
  private val relationshipsKeysRepository: RelationshipsKeysRepository,
  private val relationshipsService: RelationshipsService,
  private val endorseTrustedContactsService: EndorseTrustedContactsService,
  private val minimumLoadingDuration: MinimumLoadingDuration,
) : RecoveryInProgressDataStateMachine {
  @Composable
  override fun model(props: RecoveryInProgressProps): RecoveryInProgressData {
    var state by remember(props.fullAccountConfig, props.recovery) {
      mutableStateOf(
        calculateInitialState(props.fullAccountConfig, props.recovery)
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
          // Only allow to cancel recovery when it's initiated (while delay period is pending, or has finished),
          // or when the auth keys have been successfully rotated. If a customer is in the process of
          // rotating auth keys, we don't want to allow them to cancel recovery, since the server may
          // have already rotated, and the customer would be in a bad state. This prevents a bad state
          // in case if some parts of the completion process have already started, but failed to complete
          // for some reason (F8e or NFC error).
          canCancelRecovery = props.recovery is InitiatedRecovery || props.recovery is RotatedAuthKeys,
          startComplete = {
            scope.launch {
              state =
                AwaitingChallengeAndCsekSignedWithHardwareState(
                  challenge =
                    DelayNotifyChallenge.fromParts(
                      type = DelayNotifyChallenge.Type.RECOVERY,
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

      is RotatingAuthTokensState -> {
        LaunchedEffect("rotating tokens") {
          // If we are restoring the app from D+N, it means
          // we have lost the local + cloud data and will no longer
          // have access to the DDK, so we remove the trusted contacts
          recoveryAuthCompleter
            .rotateAuthTokens(
              f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
              fullAccountId = props.recovery.fullAccountId,
              destinationAppAuthPubKeys = AppAuthPublicKeys(
                props.recovery.appGlobalAuthKey,
                props.recovery.appRecoveryAuthKey,
                props.recovery.appGlobalAuthKeyHwSignature
              )
            )
            .onSuccess {
              dataState.sealedCsek?.let {
                // The state machine watches the recovery status, and updates automatically
                // when recovery goes from AttemptingCompletion to RotatedAuthKeys. Some users
                // have encountered a (since fixed) logic error where their state has already been
                // moved to RotatedAuthKeys -- even though they failed to save the initial state.
                //
                // This prevents the state machine from hanging, since in these cases, going from
                // RotatedAuthKeys to RotatedAuthKeys would not trigger a state change.
                state = AwaitingHardwareProofOfPossessionState(
                  sealedCsek = it
                )
              }
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
            verifyAppAuth(props)
          }
            .onSuccess {
              state = RotatingAuthTokensState(
                // If the recovery is already RotatedAuthKeys, we pass in the sealedCsek
                // and will drive the state change to AwaitingHardwareProofOfPossessionState
                sealedCsek = if (props.recovery is RotatedAuthKeys) dataState.sealedCsek else null
              )
            }
            .onFailure { error ->
              when (error) {
                is AuthProtocolError -> {
                  recoveryDao.setLocalRecoveryProgress(
                    CompletionAttemptFailedDueToServerCancellation
                  )
                }

                else -> {
                  state = FailedToRotateAuthState(cause = error)
                }
              }
            }
        }
        RotatingAuthKeysWithF8eData(props.recovery.factorToRecover)
      }

      is VerifyingNotificationCommsForCancellationState -> {
        VerifyingNotificationCommsForCancellationData(
          lostFactor = props.recovery.factorToRecover,
          f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
          fullAccountId = props.recovery.fullAccountId,
          onRollback = {
            // Take them back to the beginning
            state = calculateInitialState(props.fullAccountConfig, props.recovery)
          },
          onComplete = {
            state =
              getHwProofOfPossessionOrCancelDirectly(
                props,
                rollbackFromAwaitingProofOfPossession = {
                  state = calculateInitialState(props.fullAccountConfig, props.recovery)
                }
              )
          }
        )
      }

      is CancellingState -> {
        LaunchedEffect("cancelling-recovery") {
          cancelRecovery(
            props = props,
            request = dataState.cancellationRequest,
            onNeedsCommsVerificationError = {
              state = VerifyingNotificationCommsForCancellationState
            },
            onError = { error ->
              state =
                FailedToCancelRecoveryState(
                  cause = error,
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
            state = CancellingState(CancelLostAppAndCloudRecovery(it))
          },
          rollback = dataState.rollback,
          fullAccountId = props.recovery.fullAccountId
        )
      }

      is AwaitingChallengeAndCsekSignedWithHardwareState -> {
        AwaitingChallengeAndCsekSignedWithHardwareData(
          nfcTransaction =
            SignChallengeAndCsek(
              challenge = dataState.challenge,
              csek = dataState.csek,
              success = { response ->
                scope.launch {
                  csekDao.set(response.sealedCsek, dataState.csek)
                    .onSuccess { _ ->
                      state =
                        RotatingAuthKeysWithF8eState(
                          sealedCsek = response.sealedCsek,
                          hardwareSignedChallenge = response.signedChallenge
                        )
                    }
                    .onFailure { error ->
                      state = FailedToRotateAuthState(cause = error)
                    }
                }
              },
              failure = { state = ReadyToCompleteRecoveryState },
              isHardwareFake = props.fullAccountConfig.isHardwareFake
            )
        )
      }

      is FailedToRotateAuthState ->
        FailedToRotateAuthData(
          cause = dataState.cause,
          factorToRecover = props.recovery.factorToRecover,
          onConfirm = { state = ReadyToCompleteRecoveryState }
        )

      is RotatingAuthKeysWithF8eState -> {
        LaunchedEffect("rotate-auth-keys") {
          verifyAppAuth(props)
            .onSuccess {
              state = RotatingAuthTokensState(
                // If the recovery is already RotatedAuthKeys, we pass in the sealedCsek
                // and will drive the state change to AwaitingHardwareProofOfPossessionState
                sealedCsek = if (props.recovery is RotatedAuthKeys) dataState.sealedCsek else null
              )
            }
            .onFailure {
              rotateAuthKeys(props, dataState)
                .onSuccess {
                  state = RotatingAuthTokensState()
                }
                .onFailure { error ->
                  state = FailedToRotateAuthState(error)
                }
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
                sealedCsek = dataState.sealedCsek
              )
          },
          onRetry = {
            state = FetchingSealedDelegatedDecryptionKeyFromF8eState(
              sealedCsek = dataState.sealedCsek
            )
          }
        )
      }

      is FetchingSealedDelegatedDecryptionKeyFromF8eState -> {
        val isRecoveringApp = props.recovery.factorToRecover == App
        if (!isRecoveringApp) {
          state = AwaitingHardwareProofOfPossessionState(
            sealedCsek = dataState.sealedCsek
          )
        } else {
          LaunchedEffect("fetch-ddk-from-f8e") {
            relationshipsService.getRelationshipsWithoutSyncing(
              accountId = props.recovery.fullAccountId,
              f8eEnvironment = props.fullAccountConfig.f8eEnvironment
            )
              .onSuccess { relationships ->
                // If we don't have any existing endorsed relationships, we can proceed with the recovery
                if (relationships.protectedCustomers.isEmpty() && relationships.endorsedTrustedContacts.isEmpty()) {
                  state = AwaitingHardwareProofOfPossessionState(dataState.sealedCsek)
                } else {
                  // if we do have relationships, we need to fetch the DDK
                  delegatedDecryptionKeyService
                    .getSealedDelegatedDecryptionKeyData(
                      accountId = props.recovery.fullAccountId,
                      f8eEnvironment = props.fullAccountConfig.f8eEnvironment
                    )
                    .onSuccess { sealedDelegatedDecryptionKeyData ->
                      state = FetchingSealedDelegatedDecryptionKeyDataState(
                        sealedData = sealedDelegatedDecryptionKeyData,
                        sealedCsek = dataState.sealedCsek
                      )
                    }
                    .onFailure { error ->
                      // If we get a 404, we don't have a DDK stored in F8e and should remove TCs
                      state =
                        if (error is HttpError.ClientError && error.response.status.value == 404) {
                          RemovingTrustedContactsState(
                            sealedCsek = dataState.sealedCsek
                          )
                        } else {
                          DelegatedDecryptionKeyErrorState(
                            cause = Error(error),
                            sealedCsek = dataState.sealedCsek
                          )
                        }
                    }
                }
              }
              .onFailure { error ->
                state = DelegatedDecryptionKeyErrorState(
                  cause = Error(error),
                  sealedCsek = dataState.sealedCsek
                )
              }
          }
        }

        RotatingAuthKeysWithF8eData(props.recovery.factorToRecover)
      }

      is FetchingSealedDelegatedDecryptionKeyDataState -> {
        FetchingSealedDelegatedDecryptionKeyStringData(
          nfcTransaction = UnsealData(
            sealedData = dataState.sealedData,
            isHardwareFake = props.fullAccountConfig.isHardwareFake,
            success = { result ->
              delegatedDecryptionKeyService
                .restoreDelegatedDecryptionKey(result.unsealedData)
                .onSuccess {
                  state = AwaitingHardwareProofOfPossessionState(
                    sealedCsek = dataState.sealedCsek
                  )
                }
                .onFailure {
                  state = DelegatedDecryptionKeyErrorState(
                    cause = Error(it),
                    sealedCsek = dataState.sealedCsek
                  )
                }
            },
            failure = {
              state =
                DelegatedDecryptionKeyErrorState(
                  cause = Error("NFC Error"),
                  sealedCsek = dataState.sealedCsek
                )
            }
          )
        )
      }

      is RemovingTrustedContactsState -> {
        LaunchedEffect("remove-trusted-contacts") {
          removeTrustedContacts(props)
            .onSuccess { state = AwaitingHardwareProofOfPossessionState(dataState.sealedCsek) }
            .onFailure { state = AwaitingHardwareProofOfPossessionState(dataState.sealedCsek) }
        }

        RotatingAuthKeysWithF8eData(props.recovery.factorToRecover)
      }

      is AwaitingHardwareProofOfPossessionState -> {
        AwaitingHardwareProofOfPossessionData(
          fullAccountId = props.recovery.fullAccountId,
          fullAccountConfig = props.fullAccountConfig,
          appAuthKey = props.recovery.appGlobalAuthKey,
          addHwFactorProofOfPossession = { hardwareProofOfPossession ->
            state =
              CreatingSpendingKeysWithF8eState(dataState.sealedCsek, hardwareProofOfPossession)
          },
          rollback = {
            state = ReadyToCompleteRecoveryState
          }
        )
      }

      is CreatingSpendingKeysWithF8eState -> {
        LaunchedEffect("create-spending-keys") {
          rotateF8eSpendingKeyToCompleteRecovery(props, dataState)
            .onFailure { error ->
              state =
                FailedToCreateSpendingKeysState(
                  cause = error,
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
          cause = dataState.cause,
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
              keybox = dataState.keybox
            )
          }
        )
      }

      is PerformingDdkBackupState -> {
        if (props.recovery.factorToRecover != Hardware) {
          LaunchedEffect("set-recovery-progress-ddk-backed-up") {
            // If we're not doing a hardware recovery, we don't need
            // to reseal+upload the DDK, so we can mark as complete
            recoverySyncer.setLocalRecoveryProgress(LocalRecoveryAttemptProgress.DdkBackedUp)
          }

          RotatingAuthKeysWithF8eData(props.recovery.factorToRecover)
        } else if (dataState.delegatedDecryptionKey == null) {
          LaunchedEffect("get-or-create-ddk") {
            relationshipsKeysRepository.getKeyWithPrivateMaterialOrCreate<DelegatedDecryptionKey>()
              .onSuccess { keypair ->
                state = PerformingDdkBackupState(delegatedDecryptionKey = keypair)
              }
              .onFailure { error ->
                state = FailedPerformingDdkBackupState(cause = error)
              }
          }

          PerformingDdkBackupData(
            physicalFactor = props.recovery.factorToRecover
          )
        } else {
          SealingDelegatedDecryptionKeyData(
            nfcTransaction = SealDelegatedDecryptionKey(
              unsealedKeypair = dataState.delegatedDecryptionKey,
              isHardwareFake = props.fullAccountConfig.isHardwareFake,
              success = { sealedDataResult ->
                delegatedDecryptionKeyService.uploadSealedDelegatedDecryptionKeyData(
                  props.recovery.fullAccountId,
                  props.fullAccountConfig.f8eEnvironment,
                  sealedDataResult.sealedData
                ).onSuccess {
                  recoverySyncer.setLocalRecoveryProgress(LocalRecoveryAttemptProgress.DdkBackedUp)
                }.onFailure {
                  state = FailedPerformingDdkBackupState(
                    cause = it,
                    delegatedDecryptionKey = dataState.delegatedDecryptionKey
                  )
                }
              },
              failure = {
                state = FailedPerformingDdkBackupState(
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
              delegatedDecryptionKey = dataState.delegatedDecryptionKey
            )
          }
        )

      is PerformingCloudBackupState ->
        PerformingCloudBackupData(
          sealedCsek = dataState.sealedCsek,
          keybox = dataState.keybox,
          onBackupFinished = {
            scope.launch {
              recoverySyncer
                .setLocalRecoveryProgress(LocalRecoveryAttemptProgress.BackedUpToCloud)
            }
          },
          onBackupFailed = { error ->
            state =
              FailedPerformingCloudBackupState(
                cause = error,
                sealedCsek = dataState.sealedCsek,
                keybox = dataState.keybox
              )
          }
        )

      is PerformingSweepState ->
        PerformingSweepData(
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
        RegenerateTcCertificatesEffect(
          props,
          dataState = dataState,
          assignState = { newState -> state = newState }
        )
        RegeneratingTcCertificatesData
      }
    }
  }

  private suspend fun removeTrustedContacts(props: RecoveryInProgressProps): Result<Unit, Error> =
    coroutineBinding {
      relationshipsService
        .getRelationshipsWithoutSyncing(
          accountId = props.recovery.fullAccountId,
          f8eEnvironment = props.fullAccountConfig.f8eEnvironment
        )
        .logFailure { "Error fetching relationships for removal" }
        .onSuccess { relationships ->
          relationships.protectedCustomers.onEach {
            relationshipsService.removeRelationshipWithoutSyncing(
              accountId = props.recovery.fullAccountId,
              f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
              hardwareProofOfPossession = null,
              AuthTokenScope.Recovery,
              it.relationshipId
            )
          }
        }
        .bind()
    }

  /**
   * Verify that can authenticate with new app global and recovery auth key post recovery.
   */
  private suspend fun verifyAppAuth(props: RecoveryInProgressProps): Result<Unit, Error> =
    coroutineBinding {
      accountAuthenticator
        .appAuth(
          f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
          appAuthPublicKey = props.recovery.appGlobalAuthKey,
          authTokenScope = AuthTokenScope.Global
        )
        .logAuthFailure { "Error authenticating with new app global auth key after recovery completed." }
        .bind()

      accountAuthenticator
        .appAuth(
          f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
          appAuthPublicKey = props.recovery.appRecoveryAuthKey,
          authTokenScope = AuthTokenScope.Recovery
        )
        .logAuthFailure { "Error authenticating with new app recovery auth key after recovery completed." }
        .bind()
    }

  @Suppress("FunctionNaming")
  @Composable
  private fun RegenerateTcCertificatesEffect(
    props: RecoveryInProgressProps,
    dataState: RegeneratingTcCertificatesState,
    assignState: (State) -> Unit,
  ) {
    LaunchedEffect("regenerate-tc-certificates") {
      regenerateTcCertificates(props)
        .onSuccess {
          assignState(
            PerformingCloudBackupState(
              dataState.sealedCsek,
              dataState.keybox
            )
          )
        }
        .onFailure {
          assignState(
            State.FailedRegeneratingTcCertificatesState(
              cause = it,
              sealedCsek = dataState.sealedCsek,
              keybox = dataState.keybox
            )
          )
        }
    }
  }

  private suspend fun regenerateTcCertificates(props: RecoveryInProgressProps) =
    coroutineBinding {
      // 1. Get latest trusted contacts from f8e
      val trustedContacts = relationshipsService
        .getRelationshipsWithoutSyncing(
          accountId = props.recovery.fullAccountId,
          f8eEnvironment = props.fullAccountConfig.f8eEnvironment
        )
        .bind()
        .endorsedTrustedContacts
      // 2. Verify all trusted contacts with new auth keys
      endorseTrustedContactsService.authenticateRegenerateAndEndorse(
        f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
        accountId = props.recovery.fullAccountId,
        contacts = trustedContacts,
        oldAppGlobalAuthKey = props.oldAppGlobalAuthKey,
        oldHwAuthKey = props.recovery.hardwareAuthKey,
        newAppGlobalAuthKey = props.recovery.appGlobalAuthKey,
        newAppGlobalAuthKeyHwSignature = props.recovery.appGlobalAuthKeyHwSignature
      ).bind()

      // 3. Re-sync relationships and store locally
      relationshipsService
        .syncAndVerifyRelationships(
          accountId = props.recovery.fullAccountId,
          f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
          appAuthKey = props.recovery.appGlobalAuthKey,
          hwAuthPublicKey = props.recovery.hardwareAuthKey
        )
        .bind()
    }

  private fun createNewKeybox(
    fullAccountConfig: FullAccountConfig,
    recovery: StillRecovering,
    f8eSpendingKeyset: F8eSpendingKeyset,
  ): Keybox {
    val spendingKeyset =
      SpendingKeyset(
        localId = uuidGenerator.random(),
        f8eSpendingKeyset = f8eSpendingKeyset,
        networkType = fullAccountConfig.bitcoinNetworkType,
        appKey = recovery.appSpendingKey,
        hardwareKey = recovery.hardwareSpendingKey
      )
    return Keybox(
      localId = uuidGenerator.random(),
      fullAccountId = recovery.fullAccountId,
      activeSpendingKeyset = spendingKeyset,
      // TODO (W-9804): persist inactive keysets
      inactiveKeysets = emptyImmutableList(),
      appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
      activeAppKeyBundle =
        AppKeyBundle(
          localId = uuidGenerator.random(),
          spendingKey = recovery.appSpendingKey,
          authKey = recovery.appGlobalAuthKey,
          networkType = fullAccountConfig.bitcoinNetworkType,
          recoveryAuthKey = recovery.appRecoveryAuthKey
        ),
      activeHwKeyBundle = HwKeyBundle(
        localId = uuidGenerator.random(),
        spendingKey = recovery.hardwareSpendingKey,
        authKey = recovery.hardwareAuthKey,
        networkType = fullAccountConfig.bitcoinNetworkType
      ),
      config = fullAccountConfig
    )
  }

  /**
   * Calculate initial state based on remaining delay period.
   * If delay period is still pending, return [WaitingForDelayPeriodState].
   * Otherwise, we are ready to complete recovery, return [ReadyToCompleteRecoveryState].
   */
  private fun calculateInitialState(
    fullAccountConfig: FullAccountConfig,
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
        FetchingSealedDelegatedDecryptionKeyFromF8eState(
          sealedCsek = recovery.sealedCsek
        )
      }

      is CreatedSpendingKeys -> {
        PerformingDdkBackupState()
      }

      is DdkBackedUp -> {
        RegeneratingTcCertificatesState(
          sealedCsek = recovery.sealedCsek,
          keybox = createNewKeybox(fullAccountConfig, recovery, recovery.f8eSpendingKeyset)
        )
      }

      is BackedUpToCloud -> {
        PerformingSweepState(
          keybox = createNewKeybox(fullAccountConfig, recovery, recovery.f8eSpendingKeyset)
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
    request: CancellationRequest,
    onNeedsCommsVerificationError: () -> Unit,
    onError: (CancelDelayNotifyRecoveryError) -> Unit,
  ): Result<Unit, CancelDelayNotifyRecoveryError> {
    return when (request) {
      CancelLostHardwareRecovery ->
        lostHardwareRecoveryService.cancelRecovery(
          props.fullAccountConfig.f8eEnvironment,
          props.recovery.fullAccountId
        )
      is CancelLostAppAndCloudRecovery ->
        lostAppAndCloudRecoveryService
          .cancelRecovery(
            props.fullAccountConfig.f8eEnvironment,
            props.recovery.fullAccountId,
            request.hwProofOfPossession
          )
    }.onFailure { error ->
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
      App -> AwaitingCancellationProofOfPossessionState(rollbackFromAwaitingProofOfPossession)
      Hardware -> CancellingState(CancelLostHardwareRecovery)
    }
  }

  private suspend fun rotateAuthKeys(
    props: RecoveryInProgressProps,
    state: RotatingAuthKeysWithF8eState,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      recoveryAuthCompleter
        .rotateAuthKeys(
          f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
          fullAccountId = props.recovery.fullAccountId,
          hardwareSignedChallenge = state.hardwareSignedChallenge,
          destinationAppAuthPubKeys = AppAuthPublicKeys(
            props.recovery.appGlobalAuthKey,
            props.recovery.appRecoveryAuthKey,
            props.recovery.appGlobalAuthKeyHwSignature
          ),
          sealedCsek = state.sealedCsek
        ).bind()
    }

  private suspend fun rotateF8eSpendingKeyToCompleteRecovery(
    props: RecoveryInProgressProps,
    state: CreatingSpendingKeysWithF8eState,
  ): Result<Unit, Error> =
    coroutineBinding {
      val f8eSpendingKeyset =
        f8eSpendingKeyRotator
          .rotateSpendingKey(
            fullAccountConfig = props.fullAccountConfig,
            fullAccountId = props.recovery.fullAccountId,
            appSpendingKey = props.recovery.appSpendingKey,
            hardwareSpendingKey = props.recovery.hardwareSpendingKey,
            appAuthKey = props.recovery.appGlobalAuthKey,
            hardwareProofOfPossession = state.hardwareProofOfPossession
          )
          .bind()

      // TODO(BKR-1094): Use the recovery destination auth key, not the stale ones
      deviceTokenManager.addDeviceTokenIfPresentForAccount(
        fullAccountId = props.recovery.fullAccountId,
        f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
        authTokenScope = AuthTokenScope.Recovery
      ).result.logFailure {
        "Failed to add device token for account during Social Recovery"
      }

      recoverySyncer
        .setLocalRecoveryProgress(
          LocalRecoveryAttemptProgress.RotatedSpendingKeys(f8eSpendingKeyset)
        )
        .bind()
    }

  /**
   * Represents a request for cancelling a recovery.
   */
  private sealed interface CancellationRequest {
    /**
     * Request to cancel "Lost Hardware" recovery. Will be cancelled using
     * app proof of possession.
     */
    data object CancelLostHardwareRecovery : CancellationRequest

    /**
     * Request to cancel "Lost App and Cloud" recovery. Will be cancelled using
     * hardware proof of possession.
     */
    data class CancelLostAppAndCloudRecovery(
      val hwProofOfPossession: HwFactorProofOfPossession,
    ) : CancellationRequest
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

    data class CheckCompletionAttemptForSuccessOrCancellation(val sealedCsek: SealedCsek) : State

    data class RotatingAuthTokensState(
      val sealedCsek: SealedCsek? = null,
    ) : State

    /**
     * Awaiting for hardware to
     *
     * @property csek brand new CSEK to be sealed by hardware. Sealed CSEK will be used to backup
     * keybox after recovery is complete.
     */
    data class AwaitingChallengeAndCsekSignedWithHardwareState(
      val challenge: DelayNotifyChallenge,
      val csek: Csek,
    ) : State

    data class CancellingState(
      val cancellationRequest: CancellationRequest,
    ) : State

    /**
     * Rotating authentication keys with f8e. See [RecoveryAuthCompleter] for
     * details.
     */
    data class RotatingAuthKeysWithF8eState(
      val sealedCsek: SealedCsek,
      val hardwareSignedChallenge: SignedChallenge.HardwareSignedChallenge,
    ) : State

    data class FetchingSealedDelegatedDecryptionKeyFromF8eState(
      val sealedCsek: SealedCsek,
    ) : State

    data class FetchingSealedDelegatedDecryptionKeyDataState(
      val sealedData: SealedData,
      val sealedCsek: SealedCsek,
    ) : State

    data class DelegatedDecryptionKeyErrorState(
      val cause: Error,
      val sealedCsek: SealedCsek,
    ) : State

    data class RemovingTrustedContactsState(
      val sealedCsek: SealedCsek,
    ) : State

    data class FailedToCreateSpendingKeysState(
      val cause: Error,
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
     * Generating new TC certificates using updated auth keys.
     */
    data class RegeneratingTcCertificatesState(
      val sealedCsek: SealedCsek,
      val keybox: Keybox,
    ) : State

    data class FailedRegeneratingTcCertificatesState(
      val sealedCsek: SealedCsek,
      val keybox: Keybox,
      val cause: Error,
    ) : State

    /**
     * Creating and uploading DDK sealed with new Hardware
     */
    data class PerformingDdkBackupState(
      val delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>? = null,
    ) : State

    data class FailedPerformingDdkBackupState(
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
      val keybox: Keybox,
    ) : State

    data class ExitedPerformingSweepState(
      val keybox: Keybox,
    ) : State
  }
}

private fun CancelDelayNotifyRecoveryError.isNetworkError(): Boolean {
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
