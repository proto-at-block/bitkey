package build.wallet.statemachine.data.keybox

import androidx.compose.runtime.*
import bitkey.recovery.RecoveryStatusService
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_KEY_MISSING
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.Recovery
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
  private val recoveryStatusService: RecoveryStatusService,
) : NoActiveAccountDataStateMachine {
  @Composable
  override fun model(props: NoActiveAccountDataProps): NoActiveAccountData {
    LaunchedEffect("no-app-keybox-analytics-event") {
      eventTracker.track(ACTION_APP_OPEN_KEY_MISSING)
    }

    val recovery by remember {
      recoveryStatusService.status
    }.collectAsState()

    return when (val currentRecovery = recovery) {
      is Recovery.StillRecovering -> Recovery(
        cloudBackup = null,
        recovery = currentRecovery,
        onRollback = {
          // Data change handles this navigation.
        },
        goToLiteAccountCreation = props.goToLiteAccountCreation
      )
      else -> NoActiveKeybox(props, forceCloudRetry = false)
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
          startEmergencyExitRecovery = {
            state = EmergencyExitAccountRecoveryState
          }
        )

      is CheckCloudBackupAndRouteState ->
        CheckingCloudBackupData(
          intent = dataState.startIntent,
          inviteCode = dataState.inviteCode,
          onStartCloudRecovery = {
              cloudStoreAccount,
              backup,
            ->
            state = FullAccountRecoveryState(cloudStoreAccount, backup)
          },
          onStartLostAppRecovery = { state = FullAccountRecoveryState(null, null) },
          onImportEmergencyExitKit = { state = EmergencyExitAccountRecoveryState },
          onExit = { state = GettingStartedState }
        )

      is FullAccountRecoveryState -> Recovery(
        cloudBackup = dataState.backup,
        recovery = null,
        onRollback = {
          state = GettingStartedState
        },
        goToLiteAccountCreation = props.goToLiteAccountCreation
      )

      is EmergencyExitAccountRecoveryState ->
        RecoveringAccountWithEmergencyExitKit(
          onExit = { state = GettingStartedState }
        )
    }
  }

  @Composable
  private fun Recovery(
    cloudBackup: CloudBackup?,
    recovery: Recovery.StillRecovering?,
    onRollback: () -> Unit,
    goToLiteAccountCreation: () -> Unit,
  ) = RecoveringAccountData(
    lostAppRecoveryData = lostAppRecoveryDataStateMachine.model(
      LostAppRecoveryProps(
        cloudBackup = cloudBackup,
        activeRecovery = recovery,
        onRollback = onRollback,
        goToLiteAccountCreation = goToLiteAccountCreation
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
    val cloudStoreAccount: CloudStoreAccount?,
    val backup: CloudBackup?,
  ) : State

  /**
   * Application is in the process of recovering from the Emergency Exit Kit backup.
   */
  data object EmergencyExitAccountRecoveryState : State

  /**
   * Loading a cloud backup to determine how to proceed with recovery or account creation.
   */
  data class CheckCloudBackupAndRouteState(
    val startIntent: StartIntent,
    val inviteCode: String? = null,
  ) : State
}
