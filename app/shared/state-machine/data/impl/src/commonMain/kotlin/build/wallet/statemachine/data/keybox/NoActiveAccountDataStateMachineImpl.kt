package build.wallet.statemachine.data.keybox

import androidx.compose.runtime.*
import build.wallet.LoadableValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_KEY_MISSING
import build.wallet.asLoadableValue
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackup
import build.wallet.debug.DebugOptions
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.keybox.KeyboxDao
import build.wallet.onboarding.CreateFullAccountContext.NewFullAccount
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.data.account.create.CreateFullAccountDataProps
import build.wallet.statemachine.data.account.create.CreateFullAccountDataStateMachine
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.*
import build.wallet.statemachine.data.keybox.AccountData.StartIntent
import build.wallet.statemachine.data.keybox.State.*
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryProps
import com.github.michaelbull.result.getOr
import kotlinx.coroutines.flow.map

@BitkeyInject(AppScope::class)
class NoActiveAccountDataStateMachineImpl(
  private val createFullAccountDataStateMachine: CreateFullAccountDataStateMachine,
  private val lostAppRecoveryDataStateMachine: LostAppRecoveryDataStateMachine,
  private val eventTracker: EventTracker,
  private val keyboxDao: KeyboxDao,
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
          debugOptions = props.debugOptions,
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
    // Load onboarding keybox, returning [CheckingRecoveryOrOnboarding] until loaded
    val onboardingKeybox =
      when (val onboardingKeybox = rememberOnboardingKeybox()) {
        is LoadableValue.InitialLoading -> return CheckingRecoveryOrOnboarding
        is LoadableValue.LoadedValue -> onboardingKeybox.value
      }

    // Assign initial state based on onboarding keybox
    var state: State by remember {
      mutableStateOf(
        when {
          forceCloudRetry ->
            CheckCloudBackupAndRouteState(
              startIntent = StartIntent.RestoreBitkey
            )
          onboardingKeybox == null -> GettingStartedState()
          else -> CreateFullAccountState
        }
      )
    }

    LaunchedEffect("deep-link-routing") {
      Router.onRouteChange { route ->
        when (route) {
          is Route.TrustedContactInvite ->
            when (val s = state) {
              is CreateLiteAccountState -> {
                eventTracker.track(Action.ACTION_APP_SOCREC_ENTERED_INVITE_VIA_DEEPLINK)
                state = s.copy(inviteCode = route.inviteCode)
                return@onRouteChange true
              }
              is GettingStartedState -> {
                state =
                  CheckCloudBackupAndRouteState(
                    startIntent = StartIntent.BeTrustedContact,
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
          startFullAccountCreation = { state = CreateFullAccountState },
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
          },
          isNavigatingBack = dataState.isNavigatingBack
        )

      is CreateFullAccountState ->
        CreatingFullAccountData(
          createFullAccountData = createFullAccountDataStateMachine.model(
            props = CreateFullAccountDataProps(
              onboardingKeybox = onboardingKeybox,
              context = NewFullAccount,
              rollback = { state = GettingStartedState(isNavigatingBack = true) }
            )
          )
        )

      is CheckCloudBackupAndRouteState ->
        CheckingCloudBackupData(
          intent = dataState.startIntent,
          onStartLiteAccountRecovery = {
            state = LiteAccountRecoveryState(it)
            // TODO(BKR-746): Find a better way to defer this navigation
            // If we've gotten here with an invite code, we need to reset the route (consumed above)
            // so that we try to add the TC after the recovery is complete
            if (dataState.inviteCode != null) {
              Router.route = Route.TrustedContactInvite(dataState.inviteCode)
            }
          },
          onStartLiteAccountCreation = { state = CreateLiteAccountState(dataState.inviteCode) },
          onStartCloudRecovery = { state = FullAccountRecoveryState(it) },
          onStartLostAppRecovery = { state = FullAccountRecoveryState(null) },
          onImportEmergencyAccessKit = { state = EmergencyAccessAccountRecoveryState },
          onExit = { state = GettingStartedState(isNavigatingBack = true) }
        )

      is FullAccountRecoveryState ->
        Recovery(
          debugOptions = props.debugOptions,
          cloudBackup = dataState.backup,
          props = props,
          onRollback = {
            state = GettingStartedState()
          },
          onRetryCloudRecovery = {
            state =
              CheckCloudBackupAndRouteState(
                startIntent = StartIntent.RestoreBitkey
              )
          }
        )

      is LiteAccountRecoveryState -> {
        RecoveringLiteAccountData(
          cloudBackup = dataState.backup,
          onAccountCreated = props.onAccountCreated,
          onExit = {
            state = GettingStartedState()
          }
        )
      }

      is EmergencyAccessAccountRecoveryState ->
        NoActiveAccountData.RecoveringAccountWithEmergencyAccessKit(
          onExit = { state = GettingStartedState(isNavigatingBack = true) }
        )

      is CreateLiteAccountState ->
        CreatingLiteAccountData(
          onRollback = {
            state = GettingStartedState()
          },
          inviteCode = dataState.inviteCode,
          onAccountCreated = props.onAccountCreated
        )

      is ResetAnExistingDeviceState -> {
        ResettingExistingDeviceData(
          debugOptions = props.debugOptions,
          onExit = { state = GettingStartedState() },
          onSuccess = { state = GettingStartedState() }
        )
      }
    }
  }

  @Composable
  private fun Recovery(
    debugOptions: DebugOptions,
    cloudBackup: CloudBackup?,
    props: NoActiveAccountDataProps,
    onRollback: () -> Unit,
    onRetryCloudRecovery: () -> Unit,
  ) = RecoveringAccountData(
    debugOptions = debugOptions,
    lostAppRecoveryData = lostAppRecoveryDataStateMachine.model(
      LostAppRecoveryProps(
        cloudBackup = cloudBackup,
        fullAccountConfig = debugOptions.toFullAccountConfig(),
        activeRecovery = props.existingRecovery,
        onRollback = onRollback,
        onRetryCloudRecovery = onRetryCloudRecovery
      )
    )
  )

  @Composable
  private fun rememberOnboardingKeybox(): LoadableValue<Keybox?> {
    return remember {
      keyboxDao.onboardingKeybox()
        .map {
          // Treat DbError as null Keybox value
          it.getOr(null)
        }
        .asLoadableValue()
    }.collectAsState(LoadableValue.InitialLoading).value
  }
}

private sealed interface State {
  /**
   * Application is awaiting user action to create a new account or recover an existing one.
   */
  data class GettingStartedState(
    val isNavigatingBack: Boolean = false,
  ) : State

  /**
   * Application is in the process of creating a new full-account.
   */
  data object CreateFullAccountState : State

  /**
   * Application is in the process of creating a new lite-account.
   */
  data class CreateLiteAccountState(
    val inviteCode: String?,
  ) : State

  /**
   * Application is in the process of full account recovery using cloud backup.
   */
  data class FullAccountRecoveryState(
    val backup: CloudBackup?,
  ) : State

  /**
   * Application is in the process of lite account recovery using cloud backup.
   */
  data class LiteAccountRecoveryState(
    val backup: CloudBackup,
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
