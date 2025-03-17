package build.wallet.statemachine.data.keybox

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_KEY_MISSING
import build.wallet.cloud.backup.CloudBackup
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.*
import build.wallet.statemachine.data.keybox.AccountData.StartIntent
import build.wallet.statemachine.data.keybox.State.*
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryProps

@BitkeyInject(AppScope::class)
class NoActiveAccountDataStateMachineImpl(
  private val lostAppRecoveryDataStateMachine: LostAppRecoveryDataStateMachine,
  private val eventTracker: EventTracker,
) : NoActiveAccountDataStateMachine {
  @Composable
  override fun model(props: NoActiveAccountDataProps): NoActiveAccountData {
    LaunchedEffect("no-app-keybox-analytics-event") {
      eventTracker.track(ACTION_APP_OPEN_KEY_MISSING)
    }

    // Indicates if customer is attempting a cloud backup recovery,
    // while there is a Delay Notify already in progress.
    var retryingCloudRecovery by remember { mutableStateOf(false) }

    return when (props.existingRecovery) {
      null -> NoActiveKeybox(props, forceCloudRetry = retryingCloudRecovery)

      else ->
        Recovery(
          cloudBackup = null,
          props = props,
          onRollback = {
            // Data change handles this navigation.
          },
          onRetryCloudRecovery = {
            retryingCloudRecovery = true
          }
        )
    }
  }

  @Composable
  private fun NoActiveKeybox(
    props: NoActiveAccountDataProps,
    forceCloudRetry: Boolean = false,
  ): NoActiveAccountData {
    // Assign initial state based on onboarding keybox
    var state: State by remember {
      mutableStateOf(
        when {
          forceCloudRetry ->
            CheckCloudBackupAndRouteState(
              startIntent = StartIntent.RestoreBitkey
            )
          else -> GettingStartedState
        }
      )
    }

    LaunchedEffect("deep-link-routing") {
      Router.onRouteChange { route ->
        when (route) {
          is Route.TrustedContactInvite ->
            when (state) {
              is GettingStartedState -> {
                state = CheckCloudBackupAndRouteState(
                  startIntent = StartIntent.BeTrustedContact,
                  inviteCode = route.inviteCode
                )
                return@onRouteChange true
              }
              else -> false // no-op
            }
          is Route.BeneficiaryInvite -> when (state) {
            is GettingStartedState -> {
              state = CheckCloudBackupAndRouteState(
                startIntent = StartIntent.BeBeneficiary,
                inviteCode = route.inviteCode
              )
              return@onRouteChange true
            }
            else -> false // no-op
          }
          else -> false
        }
      }
    }

    return when (val dataState = state) {
      is GettingStartedState ->
        GettingStartedData(
          startLiteAccountCreation = {
            state = CheckCloudBackupAndRouteState(StartIntent.BeTrustedContact)
          },
          startRecovery = {
            state = CheckCloudBackupAndRouteState(StartIntent.RestoreBitkey)
          },
          startEmergencyAccessRecovery = {
            state = EmergencyAccessAccountRecoveryState
          },
          wipeExistingDevice = {
            state = ResetAnExistingDeviceState
          }
        )

      is CheckCloudBackupAndRouteState ->
        CheckingCloudBackupData(
          intent = dataState.startIntent,
          inviteCode = dataState.inviteCode,
          onStartCloudRecovery = { state = FullAccountRecoveryState(it) },
          onStartLostAppRecovery = { state = FullAccountRecoveryState(null) },
          onImportEmergencyAccessKit = { state = EmergencyAccessAccountRecoveryState },
          onExit = { state = GettingStartedState }
        )

      is FullAccountRecoveryState ->
        Recovery(
          cloudBackup = dataState.backup,
          props = props,
          onRollback = {
            state = GettingStartedState
          },
          onRetryCloudRecovery = {
            state =
              CheckCloudBackupAndRouteState(
                startIntent = StartIntent.RestoreBitkey
              )
          }
        )

      is EmergencyAccessAccountRecoveryState ->
        RecoveringAccountWithEmergencyAccessKit(
          onExit = { state = GettingStartedState }
        )

      is ResetAnExistingDeviceState -> {
        ResettingExistingDeviceData(
          onExit = { state = GettingStartedState },
          onSuccess = { state = GettingStartedState }
        )
      }
    }
  }

  @Composable
  private fun Recovery(
    cloudBackup: CloudBackup?,
    props: NoActiveAccountDataProps,
    onRollback: () -> Unit,
    onRetryCloudRecovery: () -> Unit,
  ) = RecoveringAccountData(
    lostAppRecoveryData = lostAppRecoveryDataStateMachine.model(
      LostAppRecoveryProps(
        cloudBackup = cloudBackup,
        activeRecovery = props.existingRecovery,
        onRollback = onRollback,
        onRetryCloudRecovery = onRetryCloudRecovery
      )
    )
  )
}

private sealed interface State {
  /**
   * Application is awaiting user action to create a new account or recover an existing one.
   */
  data object GettingStartedState : State

  /**
   * Application is in the process of full account recovery using cloud backup.
   */
  data class FullAccountRecoveryState(
    val backup: CloudBackup?,
  ) : State

  /**
   * Application is in the process of recovering from the emergency access kit backup.
   */
  data object EmergencyAccessAccountRecoveryState : State

  /**
   * Application is in the reset an existing device flow
   */
  data object ResetAnExistingDeviceState : State

  /**
   * Loading a cloud backup to determine how to proceed with recovery or account creation.
   */
  data class CheckCloudBackupAndRouteState(
    val startIntent: StartIntent,
    val inviteCode: String? = null,
  ) : State
}
