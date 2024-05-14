package build.wallet.statemachine.data.keybox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.LoadableValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_KEY_MISSING
import build.wallet.asLoadableValue
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackup
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.log
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.data.account.create.CreateFullAccountContext
import build.wallet.statemachine.data.account.create.CreateFullAccountDataProps
import build.wallet.statemachine.data.account.create.CreateFullAccountDataStateMachine
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CheckingCloudBackupData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CheckingRecoveryOrOnboarding
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CreatingFullAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CreatingLiteAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.RecoveringAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.RecoveringLiteAccountData
import build.wallet.statemachine.data.keybox.AccountData.StartIntent
import build.wallet.statemachine.data.keybox.State.CheckCloudBackupAndRouteState
import build.wallet.statemachine.data.keybox.State.CreateFullAccountState
import build.wallet.statemachine.data.keybox.State.CreateLiteAccountState
import build.wallet.statemachine.data.keybox.State.EmergencyAccessAccountRecoveryState
import build.wallet.statemachine.data.keybox.State.FullAccountRecoveryState
import build.wallet.statemachine.data.keybox.State.GettingStartedState
import build.wallet.statemachine.data.keybox.State.LiteAccountRecoveryState
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryProps
import com.github.michaelbull.result.getOr
import kotlinx.coroutines.flow.map

class NoActiveAccountDataStateMachineImpl(
  private val createFullAccountDataStateMachine: CreateFullAccountDataStateMachine,
  private val lostAppRecoveryDataStateMachine: LostAppRecoveryDataStateMachine,
  private val eventTracker: EventTracker,
  private val keyboxDao: KeyboxDao,
) : NoActiveAccountDataStateMachine {
  @Composable
  override fun model(props: NoActiveAccountDataProps): NoActiveAccountData {
    LaunchedEffect("log-template-config", props.templateFullAccountConfigData.config) {
      log { "No active keybox, template config used: ${props.templateFullAccountConfigData.config}" }
    }

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
          templateFullAccountConfigData = props.templateFullAccountConfigData,
          newAccountOnboardConfigData = props.newAccountOnboardConfigData,
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
          isNavigatingBack = dataState.isNavigatingBack
        )

      is CreateFullAccountState ->
        CreatingFullAccountData(
          templateFullAccountConfig = props.templateFullAccountConfigData.config,
          createFullAccountData =
            createFullAccountDataStateMachine.model(
              props =
                CreateFullAccountDataProps(
                  onboardConfig = props.newAccountOnboardConfigData.config,
                  templateFullAccountConfig = props.templateFullAccountConfigData.config,
                  onboardingKeybox = onboardingKeybox,
                  context = CreateFullAccountContext.NewFullAccount,
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
          templateFullAccountConfig = props.templateFullAccountConfigData.config,
          onExit = { state = GettingStartedState(isNavigatingBack = true) }
        )

      is CreateLiteAccountState ->
        CreatingLiteAccountData(
          onRollback = {
            state = GettingStartedState()
          },
          templateFullAccountConfig = props.templateFullAccountConfigData.config,
          inviteCode = dataState.inviteCode,
          onAccountCreated = props.onAccountCreated
        )
    }
  }

  @Composable
  private fun Recovery(
    cloudBackup: CloudBackup?,
    props: NoActiveAccountDataProps,
    onRollback: () -> Unit,
    onRetryCloudRecovery: () -> Unit,
  ) = RecoveringAccountData(
    templateFullAccountConfig = props.templateFullAccountConfigData.config,
    lostAppRecoveryData =
      lostAppRecoveryDataStateMachine.model(
        LostAppRecoveryProps(
          cloudBackup = cloudBackup,
          fullAccountConfig = props.templateFullAccountConfigData.config,
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
   * Loading a cloud backup to determine how to proceed with recovery or account creation.
   */
  data class CheckCloudBackupAndRouteState(
    val startIntent: StartIntent,
    val inviteCode: String? = null,
  ) : State
}
