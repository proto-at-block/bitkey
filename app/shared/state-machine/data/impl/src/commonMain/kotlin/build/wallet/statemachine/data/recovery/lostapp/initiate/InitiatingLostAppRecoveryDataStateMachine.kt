package build.wallet.statemachine.data.recovery.lostapp.initiate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.auth.AccountAuthTokens
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.bitkey.recovery.HardwareKeysForRecovery
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.coroutines.delayedResult
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode.COMMS_VERIFICATION_REQUIRED
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode.RECOVERY_ALREADY_EXISTS
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryService
import build.wallet.f8e.recovery.InitiateHardwareAuthService
import build.wallet.f8e.recovery.InitiateHardwareAuthService.AuthChallenge
import build.wallet.f8e.recovery.ListKeysetsService
import build.wallet.platform.random.Uuid
import build.wallet.recovery.LostAppRecoveryAuthenticator
import build.wallet.recovery.LostAppRecoveryAuthenticator.DelayNotifyLostAppAuthError
import build.wallet.recovery.LostAppRecoveryInitiator
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.AuthenticatingWithF8EViaAppData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.AwaitingAppKeysData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.AwaitingAppSignedAuthChallengeData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.AwaitingHardwareProofOfPossessionAndSpendingKeyData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.AwaitingPushNotificationPermissionData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.CancellingConflictingRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.DisplayingConflictingRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.FailedToAuthenticateWithF8EViaAppData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.FailedToCancelConflictingRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.FailedToInitiateAppAuthWithF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.FailedToInitiateLostAppWithF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.InitiatingAppAuthWithF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.InitiatingLostAppRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.ListingKeysetsFromF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.VerifyingNotificationCommsData
import build.wallet.statemachine.data.recovery.lostapp.initiate.CommsVerificationTargetAction.CancelRecovery
import build.wallet.statemachine.data.recovery.lostapp.initiate.CommsVerificationTargetAction.InitiateRecovery
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.AuthenticatingWithF8eViaHardwareState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.AwaitingHardwareKeysState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.AwaitingHardwareProofOfPossessionAndSpendingKeyState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.AwaitingHardwareSignedAuthChallengeState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.AwaitingPushNotificationPermissionState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.CancellingConflictingRecoveryWithF8eState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.DisplayingConflictingRecoveryState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.FailedToAuthenticateWithF8eViaHardwareState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.FailedToCancelConflictingRecoveryState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.FailedToInitiateHardwareAuthWithF8eState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.FailedToInitiateRecoveryWithF8eState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.InitiatingHardwareAuthWithF8eState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.InitiatingRecoveryWithF8eState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.ListingKeysetsFromF8eState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.VerifyingNotificationCommsState
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataProps
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachine
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlin.time.Duration.Companion.seconds

/**
 * Data state machine for initiating Delay * Notify recovery for Lost App case.
 *
 * Note: is not currently persistent, all states are in memory - relaunching the app during
 * initiation process will not restore latest initiation state.
 */
interface InitiatingLostAppRecoveryDataStateMachine : StateMachine<InitiatingLostAppRecoveryProps, InitiatingLostAppRecoveryData>

/**
 * @property [keyboxConfig] keybox configuration to use for initiating Lost App recovery.
 */
data class InitiatingLostAppRecoveryProps(
  val keyboxConfig: KeyboxConfig,
  val onRollback: () -> Unit,
)

class InitiatingLostAppRecoveryDataStateMachineImpl(
  private val initiateHardwareAuthService: InitiateHardwareAuthService,
  private val listKeysetsService: ListKeysetsService,
  private val cancelDelayNotifyRecoveryService: CancelDelayNotifyRecoveryService,
  private val lostAppRecoveryInitiator: LostAppRecoveryInitiator,
  private val lostAppRecoveryAuthenticator: LostAppRecoveryAuthenticator,
  private val recoveryNotificationVerificationDataStateMachine:
    RecoveryNotificationVerificationDataStateMachine,
  private val uuid: Uuid,
) : InitiatingLostAppRecoveryDataStateMachine {
  @Composable
  override fun model(props: InitiatingLostAppRecoveryProps): InitiatingLostAppRecoveryData {
    var state: State by remember { mutableStateOf(AwaitingHardwareKeysState) }

    return state.let {
      when (val dataState = it) {
        is AwaitingHardwareKeysState -> {
          AwaitingAppKeysData(
            addHardwareAuthKey = { hardwareAuthKey ->
              state = InitiatingHardwareAuthWithF8eState(hardwareAuthKey)
            },
            rollback = props.onRollback
          )
        }

        is InitiatingHardwareAuthWithF8eState -> {
          LaunchedEffect("request-challenge") {
            requestHwAuthChallenge(props, dataState)
              .onSuccess { authChallenge ->
                state =
                  AwaitingHardwareSignedAuthChallengeState(
                    authChallenge = authChallenge,
                    hardwareAuthKey = dataState.hardwareAuthKey
                  )
              }
              .onFailure { error ->
                state = FailedToInitiateHardwareAuthWithF8eState(dataState.hardwareAuthKey, error)
              }
          }

          InitiatingAppAuthWithF8eData(
            rollback = props.onRollback
          )
        }

        is FailedToInitiateHardwareAuthWithF8eState ->
          FailedToInitiateAppAuthWithF8eData(
            retry = {
              state = InitiatingHardwareAuthWithF8eState(dataState.hardwareAuthKey)
            },
            rollback = props.onRollback
          )

        is AwaitingHardwareSignedAuthChallengeState ->
          AwaitingAppSignedAuthChallengeData(
            addSignedChallenge = { hardwareSignedChallenge ->
              state =
                AuthenticatingWithF8eViaHardwareState(
                  authChallenge = dataState.authChallenge,
                  hardwareAuthKey = dataState.hardwareAuthKey,
                  signedAuthChallenge = hardwareSignedChallenge
                )
            },
            challenge = dataState.authChallenge,
            rollback = {
              state = AwaitingHardwareKeysState
            }
          )

        is AuthenticatingWithF8eViaHardwareState -> {
          LaunchedEffect("authenticate-with-hardware") {
            delayedResult(2.seconds) {
              authenticateWithHardware(props, dataState)
            }
              .onSuccess { accountAuthTokens ->
                state =
                  ListingKeysetsFromF8eState(
                    accountAuthTokens = accountAuthTokens,
                    authChallenge = dataState.authChallenge,
                    signedAuthChallenge = dataState.signedAuthChallenge,
                    hardwareAuthKey = dataState.hardwareAuthKey
                  )
              }
              .onFailure { error ->
                state =
                  FailedToAuthenticateWithF8eViaHardwareState(
                    authChallenge = dataState.authChallenge,
                    hardwareAuthKey = dataState.hardwareAuthKey,
                    signedAuthChallenge = dataState.signedAuthChallenge,
                    error = error
                  )
              }
          }
          AuthenticatingWithF8EViaAppData(
            rollback = props.onRollback
          )
        }

        is ListingKeysetsFromF8eState -> {
          LaunchedEffect("list-keysets") {
            listKeysetsService
              .listKeysets(props.keyboxConfig.f8eEnvironment, dataState.authChallenge.fullAccountId)
              .onSuccess { keysets ->
                state =
                  AwaitingHardwareProofOfPossessionAndSpendingKeyState(
                    accountAuthTokens = dataState.accountAuthTokens,
                    authChallenge = dataState.authChallenge,
                    signedAuthChallenge = dataState.signedAuthChallenge,
                    hardwareAuthKey = dataState.hardwareAuthKey,
                    existingKeysets = keysets
                  )
              }
              .onFailure { error ->
                state =
                  FailedToAuthenticateWithF8eViaHardwareState(
                    authChallenge = dataState.authChallenge,
                    hardwareAuthKey = dataState.hardwareAuthKey,
                    signedAuthChallenge = dataState.signedAuthChallenge,
                    error = error
                  )
              }
          }
          ListingKeysetsFromF8eData(
            rollback = props.onRollback
          )
        }

        is FailedToAuthenticateWithF8eViaHardwareState ->
          FailedToAuthenticateWithF8EViaAppData(
            retry = {
              state =
                AuthenticatingWithF8eViaHardwareState(
                  authChallenge = dataState.authChallenge,
                  hardwareAuthKey = dataState.hardwareAuthKey,
                  signedAuthChallenge = dataState.signedAuthChallenge
                )
            },
            rollback = props.onRollback
          )

        is AwaitingHardwareProofOfPossessionAndSpendingKeyState -> {
          AwaitingHardwareProofOfPossessionAndSpendingKeyData(
            authTokens = dataState.accountAuthTokens,
            fullAccountId = dataState.authChallenge.fullAccountId,
            network = props.keyboxConfig.networkType,
            existingHwSpendingKeys = dataState.existingKeysets.map { it.hardwareKey },
            onComplete = { proof, hardwareSpendingKey ->
              state =
                AwaitingPushNotificationPermissionState(
                  authChallenge = dataState.authChallenge,
                  signedAuthChallenge = dataState.signedAuthChallenge,
                  hardwareKeys =
                    HardwareKeysForRecovery(
                      HwKeyBundle(
                        localId = uuid.random(),
                        spendingKey = hardwareSpendingKey,
                        authKey = dataState.hardwareAuthKey,
                        networkType = props.keyboxConfig.networkType
                      )
                    ),
                  hwFactorProofOfPossession = proof
                )
            },
            rollback = {
              state = AwaitingHardwareKeysState
            }
          )
        }

        is AwaitingPushNotificationPermissionState -> {
          AwaitingPushNotificationPermissionData(
            onComplete = {
              state =
                InitiatingRecoveryWithF8eState(
                  hardwareKeys = dataState.hardwareKeys,
                  authChallenge = dataState.authChallenge,
                  signedAuthChallenge = dataState.signedAuthChallenge,
                  hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
                )
            },
            onRetreat = {
              state = AwaitingHardwareKeysState
            }
          )
        }
        is InitiatingRecoveryWithF8eState -> {
          LaunchedEffect("initiate-recovery") {
            initiateLostAppRecovery(props, dataState)
              .onFailure { error ->
                if (error.isServerInitiationError(COMMS_VERIFICATION_REQUIRED)) {
                  state =
                    VerifyingNotificationCommsState(
                      authChallenge = dataState.authChallenge,
                      signedAuthChallenge = dataState.signedAuthChallenge,
                      hardwareKeys = dataState.hardwareKeys,
                      hwFactorProofOfPossession = dataState.hwFactorProofOfPossession,
                      targetAction = InitiateRecovery
                    )
                } else if (error.isServerInitiationError(RECOVERY_ALREADY_EXISTS)) {
                  state =
                    DisplayingConflictingRecoveryState(
                      authChallenge = dataState.authChallenge,
                      signedAuthChallenge = dataState.signedAuthChallenge,
                      hardwareKeys = dataState.hardwareKeys,
                      hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
                    )
                } else {
                  state =
                    FailedToInitiateRecoveryWithF8eState(
                      authChallenge = dataState.authChallenge,
                      signedAuthChallenge = dataState.signedAuthChallenge,
                      hardwareKeys = dataState.hardwareKeys,
                      hwFactorProofOfPossession = dataState.hwFactorProofOfPossession,
                      error = error
                    )
                }
              }
          }

          InitiatingLostAppRecoveryWithF8eData(rollback = props.onRollback)
        }

        is FailedToInitiateRecoveryWithF8eState ->
          FailedToInitiateLostAppWithF8eData(
            error = dataState.error,
            retry = {
              state =
                InitiatingRecoveryWithF8eState(
                  authChallenge = dataState.authChallenge,
                  signedAuthChallenge = dataState.signedAuthChallenge,
                  hardwareKeys = dataState.hardwareKeys,
                  hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
                )
            },
            rollback = props.onRollback
          )

        is VerifyingNotificationCommsState ->
          VerifyingNotificationCommsData(
            data =
              recoveryNotificationVerificationDataStateMachine.model(
                props =
                  RecoveryNotificationVerificationDataProps(
                    f8eEnvironment = props.keyboxConfig.f8eEnvironment,
                    fullAccountId = dataState.authChallenge.fullAccountId,
                    onRollback = props.onRollback,
                    onComplete = {
                      // We try our target action on F8e again, now that the additional verification
                      // is complete
                      state =
                        when (dataState.targetAction) {
                          InitiateRecovery ->
                            InitiatingRecoveryWithF8eState(
                              authChallenge = dataState.authChallenge,
                              signedAuthChallenge = dataState.signedAuthChallenge,
                              hardwareKeys = dataState.hardwareKeys,
                              hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
                            )

                          CancelRecovery ->
                            CancellingConflictingRecoveryWithF8eState(
                              authChallenge = dataState.authChallenge,
                              signedAuthChallenge = dataState.signedAuthChallenge,
                              hardwareKeys = dataState.hardwareKeys,
                              hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
                            )
                        }
                    },
                    hwFactorProofOfPossession = dataState.hwFactorProofOfPossession,
                    lostFactor = App
                  )
              ),
            lostFactor = App
          )

        is CancellingConflictingRecoveryWithF8eState -> {
          LaunchedEffect("cancel-existing-recovery") {
            cancelDelayNotifyRecoveryService.cancel(
              props.keyboxConfig.f8eEnvironment,
              dataState.authChallenge.fullAccountId,
              hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
            )
              .onSuccess {
                state =
                  InitiatingRecoveryWithF8eState(
                    dataState.hardwareKeys,
                    dataState.authChallenge,
                    dataState.signedAuthChallenge,
                    dataState.hwFactorProofOfPossession
                  )
              }
              .onFailure {
                val f8eError = it as F8eError.SpecificClientError<CancelDelayNotifyRecoveryErrorCode>
                state =
                  if (f8eError.errorCode == CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED) {
                    VerifyingNotificationCommsState(
                      authChallenge = dataState.authChallenge,
                      signedAuthChallenge = dataState.signedAuthChallenge,
                      hardwareKeys = dataState.hardwareKeys,
                      hwFactorProofOfPossession = dataState.hwFactorProofOfPossession,
                      targetAction = CancelRecovery
                    )
                  } else {
                    FailedToCancelConflictingRecoveryState(
                      dataState.hardwareKeys,
                      dataState.authChallenge,
                      dataState.signedAuthChallenge,
                      dataState.hwFactorProofOfPossession
                    )
                  }
              }
          }
          CancellingConflictingRecoveryData
        }

        is DisplayingConflictingRecoveryState ->
          DisplayingConflictingRecoveryData(
            onCancelRecovery = {
              state =
                CancellingConflictingRecoveryWithF8eState(
                  hardwareKeys = dataState.hardwareKeys,
                  authChallenge = dataState.authChallenge,
                  signedAuthChallenge = dataState.signedAuthChallenge,
                  hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
                )
            },
            onRetreat = props.onRollback
          )

        is FailedToCancelConflictingRecoveryState ->
          FailedToCancelConflictingRecoveryData(
            onAcknowledge = { state = AwaitingHardwareKeysState }
          )
      }
    }
  }

  private suspend fun requestHwAuthChallenge(
    props: InitiatingLostAppRecoveryProps,
    state: InitiatingHardwareAuthWithF8eState,
  ): Result<AuthChallenge, Error> =
    initiateHardwareAuthService
      .start(
        f8eEnvironment = props.keyboxConfig.f8eEnvironment,
        currentHardwareAuthKey = state.hardwareAuthKey
      )

  private suspend fun authenticateWithHardware(
    props: InitiatingLostAppRecoveryProps,
    state: AuthenticatingWithF8eViaHardwareState,
  ): Result<AccountAuthTokens, DelayNotifyLostAppAuthError> =
    lostAppRecoveryAuthenticator
      .authenticate(
        keyboxConfig = props.keyboxConfig,
        fullAccountId = state.authChallenge.fullAccountId,
        authResponseSessionToken = state.authChallenge.session,
        hardwareAuthSignature = state.signedAuthChallenge,
        hardwareAuthPublicKey = state.hardwareAuthKey
      )

  private suspend fun initiateLostAppRecovery(
    props: InitiatingLostAppRecoveryProps,
    state: InitiatingRecoveryWithF8eState,
  ) = lostAppRecoveryInitiator
    .initiate(
      keyboxConfig = props.keyboxConfig,
      hardwareKeysForRecovery =
        HardwareKeysForRecovery(
          newKeyBundle = state.hardwareKeys.newKeyBundle
        ),
      f8eEnvironment = props.keyboxConfig.f8eEnvironment,
      fullAccountId = state.authChallenge.fullAccountId,
      hwFactorProofOfPossession = state.hwFactorProofOfPossession
    )

  private sealed interface State {
    /**
     * Corresponds to [AwaitingAppKeysData].
     */
    data object AwaitingHardwareKeysState : State

    /**
     * Corresponds to [InitiatingAppAuthWithF8eData].
     */
    data class InitiatingHardwareAuthWithF8eState(
      val hardwareAuthKey: HwAuthPublicKey,
    ) : State

    /**
     * [InitiatingHardwareAuthWithF8eState] failed.
     */
    data class FailedToInitiateHardwareAuthWithF8eState(
      val hardwareAuthKey: HwAuthPublicKey,
      val error: Error,
    ) : State

    /**
     * Corresponds to [AwaitingAppSignedAuthChallengeData].
     */
    data class AwaitingHardwareSignedAuthChallengeState(
      val hardwareAuthKey: HwAuthPublicKey,
      val authChallenge: AuthChallenge,
    ) : State

    data class AuthenticatingWithF8eViaHardwareState(
      val hardwareAuthKey: HwAuthPublicKey,
      val authChallenge: AuthChallenge,
      val signedAuthChallenge: String,
    ) : State

    data class ListingKeysetsFromF8eState(
      val accountAuthTokens: AccountAuthTokens,
      val hardwareAuthKey: HwAuthPublicKey,
      val authChallenge: AuthChallenge,
      val signedAuthChallenge: String,
    ) : State

    /**
     * [AuthenticatingWithF8eViaHardwareState] failed.
     */
    data class FailedToAuthenticateWithF8eViaHardwareState(
      val hardwareAuthKey: HwAuthPublicKey,
      val authChallenge: AuthChallenge,
      val signedAuthChallenge: String,
      val error: Error,
    ) : State

    /**
     * Corresponds to [AwaitingAppSignedAuthChallengeData].
     */
    data class AwaitingHardwareProofOfPossessionAndSpendingKeyState(
      val accountAuthTokens: AccountAuthTokens,
      val hardwareAuthKey: HwAuthPublicKey,
      val authChallenge: AuthChallenge,
      val signedAuthChallenge: String,
      val existingKeysets: List<SpendingKeyset>,
    ) : State

    /**
     * Corresponds to [AwaitingPushNotificationPermissionData].
     */
    data class AwaitingPushNotificationPermissionState(
      val hardwareKeys: HardwareKeysForRecovery,
      val authChallenge: AuthChallenge,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * Corresponds to [CancellingConflictingRecoveryData].
     */
    data class CancellingConflictingRecoveryWithF8eState(
      val hardwareKeys: HardwareKeysForRecovery,
      val authChallenge: AuthChallenge,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * [CancellingConflictingRecoveryWithF8eState] failed.
     */
    data class FailedToCancelConflictingRecoveryState(
      val hardwareKeys: HardwareKeysForRecovery,
      val authChallenge: AuthChallenge,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * Corresponds to [InitiatingLostAppRecoveryWithF8eData].
     */
    data class InitiatingRecoveryWithF8eState(
      val hardwareKeys: HardwareKeysForRecovery,
      val authChallenge: AuthChallenge,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    data class DisplayingConflictingRecoveryState(
      val hardwareKeys: HardwareKeysForRecovery,
      val authChallenge: AuthChallenge,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * [InitiatingRecoveryWithF8eState] failed.
     */
    data class FailedToInitiateRecoveryWithF8eState(
      val hardwareKeys: HardwareKeysForRecovery,
      val authChallenge: AuthChallenge,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
      val error: Error,
    ) : State

    data class VerifyingNotificationCommsState(
      val hardwareKeys: HardwareKeysForRecovery,
      val authChallenge: AuthChallenge,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
      val targetAction: CommsVerificationTargetAction,
    ) : State
  }
}

/**
 * Comms verification could be required for multiple actions. These actions are enumerated here.
 */
sealed interface CommsVerificationTargetAction {
  data object CancelRecovery : CommsVerificationTargetAction

  data object InitiateRecovery : CommsVerificationTargetAction
}

private fun LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError.isServerInitiationError(
  type: InitiateAccountDelayNotifyErrorCode,
): Boolean {
  if (this !is LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError.F8eInitiateDelayNotifyError) {
    return false
  }

  if (error !is F8eError.SpecificClientError) {
    return false
  }

  val f8eError = error as F8eError.SpecificClientError<InitiateAccountDelayNotifyErrorCode>
  return f8eError.errorCode == type
}
