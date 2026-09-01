package build.wallet.statemachine.root

import androidx.compose.runtime.*
import bitkey.metrics.MetricOutcome
import bitkey.metrics.MetricTrackerService
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
import build.wallet.feature.flags.AgeRangeVerificationFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.mapResult
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.recovery.Recovery
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.account.ChooseAccountAccessUiProps
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachine
import build.wallet.statemachine.core.AgeRestrictedBodyModel
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiProps
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachine
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachine
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachineProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachine
import build.wallet.statemachine.root.metrics.AgeRangeVerificationMetricDefinition
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot

/**
 * Help Center article explaining the 18+ age requirement for the Bitkey app.
 *
 * Per Help Center convention, the app only links bitkey.world URLs which redirect to the
 * Zendesk-hosted article ("How do I download the Bitkey app to my phone?",
 * support.bitkey.world article 18827984475924), so URL changes don't require app releases.
 */
private const val AGE_REQUIREMENT_HELP_CENTER_URL = "https://bitkey.world/hc/age-requirement"

@BitkeyInject(ActivityScope::class)
class NoActiveAccountUiStateMachineImpl(
  private val lostAppRecoveryUiStateMachine: LostAppRecoveryUiStateMachine,
  private val chooseAccountAccessUiStateMachine: ChooseAccountAccessUiStateMachine,
  private val accessCloudBackupUiStateMachine: AccessCloudBackupUiStateMachine,
  private val emergencyExitKitRecoveryUiStateMachine: EmergencyExitKitRecoveryUiStateMachine,
  private val accountService: AccountService,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val ageRangeVerificationService: AgeRangeVerificationService,
  private val ageRangeVerificationFeatureFlag: AgeRangeVerificationFeatureFlag,
  private val appVariant: AppVariant,
  private val metricTrackerService: MetricTrackerService,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
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
          // User is not onboarded — consume the route so the app opens normally.
          is Route.HardwareSetup -> true
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
    // Incrementing [verificationAttempt] re-runs the verification (used by "Try again"
    // on the age restricted screen).
    var verificationAttempt by remember { mutableStateOf(0) }
    var showingAgeRestrictedHelp by remember { mutableStateOf(false) }
    val result by produceState<AgeRangeVerificationResult?>(
      initialValue = null,
      key1 = verificationAttempt
    ) {
      value = null
      // Only track the metric when the feature flag is enabled — when disabled, the service
      // short-circuits to Allowed without performing a real platform age check, which would
      // otherwise pollute the check-volume metric. The Emergency (EEK) variant also
      // short-circuits the check, so it is explicitly excluded regardless of the flag.
      val trackMetric = appVariant != AppVariant.Emergency &&
        ageRangeVerificationFeatureFlag.isEnabled()
      val verificationResult = ageRangeVerificationService.verifyAgeRange()
      // Privacy: denied checks intentionally emit NO metric at all, honoring the design
      // guarantee of "no tracking of blocked users". Block rate is instead approximated from
      // AGE_RESTRICTED screen views vs. this metric's volume. We intentionally record the
      // metric after the result is known: a check that never completes shows up as absent
      // volume, cross-checked on the dashboard.
      if (trackMetric && verificationResult == AgeRangeVerificationResult.Allowed) {
        metricTrackerService.startMetric(AgeRangeVerificationMetricDefinition)
        metricTrackerService.completeMetric(
          metricDefinition = AgeRangeVerificationMetricDefinition,
          outcome = MetricOutcome.Succeeded
        )
      }
      value = verificationResult
    }

    if (showingAgeRestrictedHelp) {
      return InAppBrowserModel(
        open = {
          inAppBrowserNavigator.open(
            url = AGE_REQUIREMENT_HELP_CENTER_URL,
            onClose = { showingAgeRestrictedHelp = false }
          )
        }
      ).asModalScreen()
    }

    return when (result) {
      null -> AppLoadingScreenModel()
      AgeRangeVerificationResult.Denied ->
        AgeRestrictedBodyModel(
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
          onLearnMore = { showingAgeRestrictedHelp = true },
          onRetry = { verificationAttempt++ }
        ).asRootScreen()
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
