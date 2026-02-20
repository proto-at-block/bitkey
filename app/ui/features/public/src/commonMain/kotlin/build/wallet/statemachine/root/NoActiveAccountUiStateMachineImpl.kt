package build.wallet.statemachine.root

import androidx.compose.runtime.*
import bitkey.recovery.RecoveryStatusService
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_KEY_MISSING
import build.wallet.availability.AgeRangeVerificationResult
import build.wallet.availability.AgeRangeVerificationService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.mapResult
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.recovery.Recovery
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.account.ChooseAccountAccessUiProps
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachine
import build.wallet.statemachine.core.AgeRestrictedBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiProps
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachine
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachine
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachineProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachine
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot

@BitkeyInject(ActivityScope::class)
class NoActiveAccountUiStateMachineImpl(
  private val lostAppRecoveryUiStateMachine: LostAppRecoveryUiStateMachine,
  private val chooseAccountAccessUiStateMachine: ChooseAccountAccessUiStateMachine,
  private val accessCloudBackupUiStateMachine: AccessCloudBackupUiStateMachine,
  private val emergencyExitKitRecoveryUiStateMachine: EmergencyExitKitRecoveryUiStateMachine,
  private val accountService: AccountService,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val ageRangeVerificationService: AgeRangeVerificationService,
  private val eventTracker: EventTracker,
  private val recoveryStatusService: RecoveryStatusService,
) : NoActiveAccountUiStateMachine {
  @Composable
  override fun model(props: NoActiveAccountUiProps): ScreenModel {
    // Track analytics for no active account state
    LaunchedEffect("no-app-keybox-analytics-event") {
      eventTracker.track(ACTION_APP_OPEN_KEY_MISSING)
    }

    // This mimics the legacy behavior of auto switching when going from NoActiveAccountData to
    // AccountData. This should be removed once DSMs are removed and there is proper routing.
    val account = rememberActiveAccount()
    if (account.get() is FullAccount) {
      props.onViewFullAccount(account.get() as FullAccount)
    }

    // Check for existing recovery in progress
    val recovery by remember {
      recoveryStatusService.status
    }.collectAsState()

    return when (val currentRecovery = recovery) {
      is Recovery.StillRecovering -> RecoveryScreen(
        cloudBackups = emptyImmutableList(),
        recovery = currentRecovery,
        onRollback = {
          // Data change handles this navigation.
        },
        goToLiteAccountCreation = props.goToLiteAccountCreation
      )
      else -> NoActiveKeyboxScreen(props)
    }
  }

  @Composable
  private fun NoActiveKeyboxScreen(props: NoActiveAccountUiProps): ScreenModel {
    // Internal UI state
    var uiState: State by remember {
      mutableStateOf(State.GettingStarted)
    }

    // Handle deep link routing
    LaunchedEffect("deep-link-routing") {
      Router.onRouteChange { route ->
        when (route) {
          is Route.TrustedContactInvite ->
            when (uiState) {
              is State.GettingStarted -> {
                uiState = State.CheckingCloudBackup(
                  startIntent = StartIntent.BeTrustedContact,
                  inviteCode = route.inviteCode
                )
                return@onRouteChange true
              }
              else -> false // no-op
            }
          is Route.BeneficiaryInvite -> when (uiState) {
            is State.GettingStarted -> {
              uiState = State.CheckingCloudBackup(
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

    return when (val state = uiState) {
      is State.GettingStarted -> GettingStartedScreen(
        props = props,
        onStartLiteAccountCreation = {
          uiState = State.CheckingCloudBackup(StartIntent.BeTrustedContact)
        },
        onStartRecovery = {
          uiState = State.CheckingCloudBackup(StartIntent.RestoreBitkey)
        },
        onStartEmergencyExitRecovery = {
          uiState = State.EmergencyExitRecovery
        }
      )

      is State.CheckingCloudBackup -> accessCloudBackupUiStateMachine.model(
        AccessCloudBackupUiProps(
          startIntent = state.startIntent,
          inviteCode = state.inviteCode,
          onExit = { uiState = State.GettingStarted },
          onStartCloudRecovery = { cloudStoreAccount, backups ->
            uiState = State.FullAccountRecovery(cloudStoreAccount, backups.toImmutableList())
          },
          onStartLiteAccountRecovery = props.onStartLiteAccountRecovery,
          onStartLostAppRecovery = {
            uiState = State.FullAccountRecovery(
              cloudStoreAccount = null,
              backups = emptyImmutableList()
            )
          },
          onStartLiteAccountCreation = props.onStartLiteAccountCreation,
          onImportEmergencyExitKit = { uiState = State.EmergencyExitRecovery },
          showErrorOnBackupMissing = when (state.startIntent) {
            StartIntent.RestoreBitkey -> true
            StartIntent.BeTrustedContact, StartIntent.BeBeneficiary -> false
          }
        )
      )

      is State.FullAccountRecovery -> RecoveryScreen(
        cloudBackups = state.backups,
        recovery = null,
        onRollback = { uiState = State.GettingStarted },
        goToLiteAccountCreation = props.goToLiteAccountCreation
      )

      is State.EmergencyExitRecovery -> emergencyExitKitRecoveryUiStateMachine.model(
        EmergencyExitKitRecoveryUiStateMachineProps(
          onExit = { uiState = State.GettingStarted }
        )
      )
    }
  }

  @Composable
  private fun GettingStartedScreen(
    props: NoActiveAccountUiProps,
    onStartLiteAccountCreation: () -> Unit,
    onStartRecovery: () -> Unit,
    onStartEmergencyExitRecovery: () -> Unit,
  ): ScreenModel {
    // Age range verification for App Store Accountability Act compliance (Texas SB2420).
    // Checks platform age signals before allowing account creation.
    val result by produceState<AgeRangeVerificationResult?>(initialValue = null) {
      value = ageRangeVerificationService.verifyAgeRange()
    }

    return when (result) {
      null -> AppLoadingScreenModel()
      AgeRangeVerificationResult.Denied ->
        AgeRestrictedBodyModel(deviceInfoProvider.getDeviceInfo().devicePlatform)
          .asRootScreen()
      AgeRangeVerificationResult.Allowed ->
        chooseAccountAccessUiStateMachine.model(
          props = ChooseAccountAccessUiProps(
            onStartLiteAccountCreation = onStartLiteAccountCreation,
            onStartRecovery = onStartRecovery,
            onStartEmergencyExitRecovery = onStartEmergencyExitRecovery,
            onSoftwareWalletCreated = props.onSoftwareWalletCreated,
            onCreateFullAccount = props.onCreateFullAccount
          )
        )
    }
  }

  @Composable
  private fun RecoveryScreen(
    cloudBackups: ImmutableList<CloudBackup>,
    recovery: Recovery.StillRecovering?,
    onRollback: () -> Unit,
    goToLiteAccountCreation: () -> Unit,
  ): ScreenModel =
    lostAppRecoveryUiStateMachine.model(
      LostAppRecoveryUiProps(
        cloudBackups = cloudBackups,
        activeRecovery = recovery,
        onRollback = onRollback,
        goToLiteAccountCreation = goToLiteAccountCreation
      )
    )

  @Composable
  private fun rememberActiveAccount() =
    remember {
      accountService.accountStatus()
        .mapResult { (it as? AccountStatus.ActiveAccount)?.account }
        // Software and lite accounts do not rely on the account DSM; filter them out so that this DSM
        // does not reset app state when a software account is activated.
        .filterNot { it.get() is SoftwareAccount || it.get() is LiteAccount }
        .distinctUntilChanged()
    }.collectAsState(Ok(null)).value

  @Composable
  private fun AppLoadingScreenModel(): ScreenModel =
    LoadingSuccessBodyModel(
      id = GeneralEventTrackerScreenId.LOADING_APP,
      state = LoadingSuccessBodyModel.State.Loading
    ).asRootScreen()

  /**
   * Internal UI states for the no active account flow.
   */
  private sealed interface State {
    /**
     * Application is awaiting user action to create a new account or recover an existing one.
     */
    data object GettingStarted : State

    /**
     * Loading a cloud backup to determine how to proceed with recovery or account creation.
     */
    data class CheckingCloudBackup(
      val startIntent: StartIntent,
      val inviteCode: String? = null,
    ) : State

    /**
     * Application is in the process of full account recovery using cloud backup.
     */
    data class FullAccountRecovery(
      val cloudStoreAccount: CloudStoreAccount?,
      val backups: ImmutableList<CloudBackup>,
    ) : State

    /**
     * Application is in the process of recovering from the Emergency Exit Kit backup.
     */
    data object EmergencyExitRecovery : State
  }
}
