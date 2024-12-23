package build.wallet.statemachine.root

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bootstrap.AppState
import build.wallet.bootstrap.LoadAppService
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inappsecurity.BiometricAuthService
import build.wallet.logging.logDebug
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.account.ChooseAccountAccessUiProps
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachine
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiProps
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiStateMachine
import build.wallet.statemachine.biometric.BiometricPromptProps
import build.wallet.statemachine.biometric.BiometricPromptUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.keybox.AccountData.*
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.*
import build.wallet.statemachine.data.keybox.AccountDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.GeneratingNewAppKeysData
import build.wallet.statemachine.dev.DebugMenuProps
import build.wallet.statemachine.dev.DebugMenuStateMachine
import build.wallet.statemachine.home.full.HomeUiProps
import build.wallet.statemachine.home.full.HomeUiStateMachine
import build.wallet.statemachine.home.lite.LiteHomeUiProps
import build.wallet.statemachine.home.lite.LiteHomeUiStateMachine
import build.wallet.statemachine.recovery.cloud.*
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiStateMachine
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiStateMachine
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRecoveryUiStateMachine
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRecoveryUiStateMachineProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceProps
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceUiStateMachine
import build.wallet.statemachine.settings.showDebugMenu
import build.wallet.statemachine.start.GettingStartedRoutingProps
import build.wallet.statemachine.start.GettingStartedRoutingStateMachine
import build.wallet.time.Delayer
import build.wallet.worker.AppWorkerExecutor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(ActivityScope::class)
class AppUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val delayer: Delayer,
  private val debugMenuStateMachine: DebugMenuStateMachine,
  private val eventTracker: EventTracker,
  private val lostAppRecoveryUiStateMachine: LostAppRecoveryUiStateMachine,
  private val homeUiStateMachine: HomeUiStateMachine,
  private val liteHomeUiStateMachine: LiteHomeUiStateMachine,
  private val chooseAccountAccessUiStateMachine: ChooseAccountAccessUiStateMachine,
  private val createAccountUiStateMachine: CreateAccountUiStateMachine,
  private val accountDataStateMachine: AccountDataStateMachine,
  private val loadAppService: LoadAppService,
  private val noLongerRecoveringUiStateMachine: NoLongerRecoveringUiStateMachine,
  private val someoneElseIsRecoveringUiStateMachine: SomeoneElseIsRecoveringUiStateMachine,
  private val gettingStartedRoutingStateMachine: GettingStartedRoutingStateMachine,
  private val createLiteAccountUiStateMachine: CreateLiteAccountUiStateMachine,
  private val liteAccountCloudBackupRestorationUiStateMachine:
    LiteAccountCloudBackupRestorationUiStateMachine,
  private val emergencyAccessKitRecoveryUiStateMachine: EmergencyAccessKitRecoveryUiStateMachine,
  private val authKeyRotationUiStateMachine: RotateAuthKeyUIStateMachine,
  private val wipingDeviceUiStateMachine: WipingDeviceUiStateMachine,
  private val appWorkerExecutor: AppWorkerExecutor,
  private val biometricAuthService: BiometricAuthService,
  private val biometricPromptUiStateMachine: BiometricPromptUiStateMachine,
) : AppUiStateMachine {
  /**
   * The last screen model emitted, if any.
   *
   * We keep track of this in order to only show [AppLoadingScreenModel] at the very beginning
   * of app startup, before other screens have been shown, and in order to show loading screens
   * back to back.
   */
  private var previousScreenModel: ScreenModel? = null

  @Composable
  override fun model(props: Unit): ScreenModel {
    LaunchedEffect("execute-app-workers") {
      appWorkerExecutor.executeAll()
    }

    var softwareAccount by remember { mutableStateOf<SoftwareAccount?>(null) }
    var accountData by remember { mutableStateOf<AccountData?>(null) }

    val appState by produceState<AppState?>(initialValue = null) {
      value = loadAppService.loadAppState()
    }

    var screenModel = softwareAccount?.let {
      HasActiveSoftwareAccountScreenModel(it, isNewlyCreated = true)
    } ?: when (val appState = appState) {
      null -> AppLoadingScreenModel()
      is AppState.HasActiveSoftwareAccount -> {
        HasActiveSoftwareAccountScreenModel(appState.account, isNewlyCreated = false)
      }
      else -> {
        accountDataStateMachine.model(Unit).let {
          accountData = it
          AppLoadedDataScreenModel(
            accountData = it,
            onSoftwareWalletCreated = { swAccount ->
              softwareAccount = swAccount
            }
          )
        }
      }
    }

    LogScreenModelEffect(screenModel)
    TrackScreenEvents(screenModel)

    // If we are showing the Splash screen and then try to show a loading screen,
    // continue to show the Splash screen for more seamless app startup experience
    val shouldContinueToShowSplashScreen = remember(previousScreenModel, screenModel) {
      (previousScreenModel?.body is SplashBodyModel) &&
        (screenModel.body as? LoadingSuccessBodyModel)?.state ==
        LoadingSuccessBodyModel.State.Loading
    }
    if (shouldContinueToShowSplashScreen) {
      screenModel = SplashScreenModel()
    }

    // As soon as we first launch, manually show the splash screen for 2 seconds, ignoring
    // all other screen model emissions, so the splash screen can show its animation.
    // Once the animation completes, whatever `screenModel` has been changed to will show.
    var isAnimatingSplashScreen by remember { mutableStateOf(previousScreenModel == null) }
    if (isAnimatingSplashScreen) {
      LaunchedEffect("show-splash-for-animation") {
        delayer.delay(2.seconds)
        isAnimatingSplashScreen = false
      }
      // Early return the splash screen model here while we let it animate.
      return SplashScreenModel()
    }

    // Set up an app-wide handler to show the debug menu.
    var isShowingDebugMenu by remember { mutableStateOf(false) }
    if (isShowingDebugMenu) {
      accountData?.let {
        return debugMenuStateMachine.model(
          props = DebugMenuProps(
            onClose = { isShowingDebugMenu = false }
          )
        )
      }
    }

    previousScreenModel = screenModel

    return when {
      appVariant.showDebugMenu ->
        screenModel.copy(onTwoFingerDoubleTap = { isShowingDebugMenu = true })
      else -> screenModel
    }
  }

  @Composable
  private fun HasActiveSoftwareAccountScreenModel(
    account: SoftwareAccount,
    isNewlyCreated: Boolean,
  ): ScreenModel {
    var shouldShowWelcomeScreenWhenTransitionToActive by remember {
      mutableStateOf(isNewlyCreated)
    }

    // If we just created the software account, we want to briefly show a welcome screen.
    return if (shouldShowWelcomeScreenWhenTransitionToActive) {
      LaunchedEffect("show-welcome-screen") {
        delayer.delay(3.seconds)
        shouldShowWelcomeScreenWhenTransitionToActive = false
      }
      LoadingSuccessBodyModel(
        id = null,
        message = "Welcome to Bitkey",
        state = LoadingSuccessBodyModel.State.Success
      ).asRootScreen()
    } else {
      homeUiStateMachine.model(
        props = HomeUiProps(
          account = account,
          lostHardwareRecoveryData = GeneratingNewAppKeysData
        )
      )
    }
  }

  @Composable
  private fun AppLoadedDataScreenModel(
    accountData: AccountData,
    onSoftwareWalletCreated: (SoftwareAccount) -> Unit,
  ): ScreenModel {
    // Keep track of when to show the "Welcome to Bitkey" screen.
    // We want to show it when we transition from NoActiveAccount -> HasActiveFullAccount
    var shouldShowWelcomeScreenWhenTransitionToActive by remember {
      mutableStateOf(false)
    }

    return when (accountData) {
      is CheckingActiveAccountData ->
        AppLoadingScreenModel()

      is NoActiveAccountData -> {
        shouldShowWelcomeScreenWhenTransitionToActive = true
        NoActiveAccountDataScreenModel(accountData, onSoftwareWalletCreated)
      }

      is HasActiveFullAccountData ->
        HasActiveFullAccountDataScreenModel(
          accountData = accountData,
          initialShouldShowWelcomeScreenWhenTransitionToActive = shouldShowWelcomeScreenWhenTransitionToActive
        )

      is HasActiveLiteAccountData ->
        liteHomeUiStateMachine.model(
          props = LiteHomeUiProps(
            accountData = accountData
          )
        )

      is NoLongerRecoveringFullAccountData ->
        noLongerRecoveringUiStateMachine.model(
          props = NoLongerRecoveringUiProps(
            canceledRecoveryLostFactor = accountData.canceledRecoveryLostFactor
          )
        )

      is SomeoneElseIsRecoveringFullAccountData ->
        someoneElseIsRecoveringUiStateMachine.model(
          props = SomeoneElseIsRecoveringUiProps(
            data = accountData.data,
            fullAccountConfig = accountData.fullAccountConfig,
            fullAccountId = accountData.fullAccountId
          )
        )
    }
  }

  @Composable
  private fun NoActiveAccountDataScreenModel(
    accountData: NoActiveAccountData,
    onSoftwareWalletCreated: (SoftwareAccount) -> Unit,
  ): ScreenModel {
    return when (accountData) {
      is CheckingRecoveryOrOnboarding -> AppLoadingScreenModel()

      is GettingStartedData ->
        ChooseAccountAccessScreenModel(
          chooseAccountAccessData = accountData,
          onSoftwareWalletCreated = onSoftwareWalletCreated
        )

      is RecoveringAccountData ->
        lostAppRecoveryUiStateMachine.model(
          LostAppRecoveryUiProps(
            recoveryData = accountData.lostAppRecoveryData,
            debugOptions = accountData.debugOptions
          )
        )

      is RecoveringLiteAccountData ->
        liteAccountCloudBackupRestorationUiStateMachine.model(
          props = LiteAccountCloudBackupRestorationUiProps(
            cloudBackup = accountData.cloudBackup,
            onLiteAccountRestored = accountData.onAccountCreated,
            onExit = accountData.onExit
          )
        )

      is RecoveringAccountWithEmergencyAccessKit ->
        emergencyAccessKitRecoveryUiStateMachine.model(
          EmergencyAccessKitRecoveryUiStateMachineProps(
            onExit = accountData.onExit
          )
        )

      is CreatingFullAccountData ->
        createAccountUiStateMachine.model(
          props = CreateAccountUiProps(
            createFullAccountData = accountData.createFullAccountData
          )
        )

      is CheckingCloudBackupData ->
        gettingStartedRoutingStateMachine.model(
          GettingStartedRoutingProps(
            startIntent = accountData.intent,
            onStartLiteAccountRecovery = accountData.onStartLiteAccountRecovery,
            onStartCloudRecovery = accountData.onStartCloudRecovery,
            onStartLostAppRecovery = accountData.onStartLostAppRecovery,
            onStartLiteAccountCreation = accountData.onStartLiteAccountCreation,
            onImportEmergencyAccessKit = accountData.onImportEmergencyAccessKit,
            onExit = accountData.onExit
          )
        )

      is CreatingLiteAccountData ->
        createLiteAccountUiStateMachine.model(
          props = CreateLiteAccountUiProps(
            onBack = accountData.onRollback,
            inviteCode = accountData.inviteCode,
            onAccountCreated = accountData.onAccountCreated,
            // If this flow was reached via invite code, show the introduction screen.
            showBeTrustedContactIntroduction = accountData.inviteCode != null
          )
        )

      is ResettingExistingDeviceData ->
        wipingDeviceUiStateMachine.model(
          props = WipingDeviceProps(
            fullAccountConfig = accountData.debugOptions.toFullAccountConfig(),
            onBack = accountData.onExit,
            onSuccess = accountData.onSuccess,
            fullAccount = null
          )
        )
    }
  }

  @Composable
  private fun HasActiveFullAccountDataScreenModel(
    accountData: HasActiveFullAccountData,
    initialShouldShowWelcomeScreenWhenTransitionToActive: Boolean,
  ): ScreenModel {
    var shouldShowWelcomeScreenWhenTransitionToActive by remember {
      mutableStateOf(initialShouldShowWelcomeScreenWhenTransitionToActive)
    }

    val shouldPromptForAuth by remember { biometricAuthService.isBiometricAuthRequired() }
      .collectAsState()

    return when (accountData) {
      is ActiveFullAccountLoadedData ->
        // If we are transitioning from NoActiveAccount to HasActiveFullAccount, we want to briefly
        // show a welcome screen. We do it here (in the UI state machine) because this is purely
        // an additional UI nicety that should not affect underlying app data state.
        if (shouldShowWelcomeScreenWhenTransitionToActive) {
          LaunchedEffect("show-welcome-screen") {
            delayer.delay(3.seconds)
            shouldShowWelcomeScreenWhenTransitionToActive = false
          }
          LoadingSuccessBodyModel(
            id = null,
            message = "Welcome to Bitkey",
            state = LoadingSuccessBodyModel.State.Success
          ).asRootScreen()
        } else {
          val homeScreenModel = HomeScreenModel(
            accountData = accountData
          )

          biometricPromptUiStateMachine.model(
            props = BiometricPromptProps(
              shouldPromptForAuth = shouldPromptForAuth
            )
          ) ?: homeScreenModel
        }

      is HasActiveFullAccountData.RotatingAuthKeys ->
        authKeyRotationUiStateMachine.model(
          RotateAuthKeyUIStateMachineProps(
            account = accountData.account,
            origin = RotateAuthKeyUIOrigin.PendingAttempt(accountData.pendingAttempt)
          )
        )
    }
  }

  @Composable
  private fun AppLoadingScreenModel(): ScreenModel {
    // Determine which loading screen to show for overall app loading based on
    // what is currently on the screen. We only want the splash screen to show
    // at the very beginning. Otherwise, we want to try and optimize loading
    // screens together, or else show a distinct "loading_app" loading screen.
    return when (val previousScreenModel = previousScreenModel) {
      null -> SplashScreenModel()
      else ->
        when {
          previousScreenModel.body is SplashBodyModel ||
            (previousScreenModel.body as? LoadingSuccessBodyModel)?.state ==
            LoadingSuccessBodyModel.State.Loading
          ->
            previousScreenModel

          else ->
            LoadingSuccessBodyModel(
              id = GeneralEventTrackerScreenId.LOADING_APP,
              state = LoadingSuccessBodyModel.State.Loading
            ).asRootScreen()
        }
    }
  }

  @Composable
  private fun SplashScreenModel(): ScreenModel =
    SplashBodyModel(
      bitkeyWordMarkAnimationDelay = 700.milliseconds,
      bitkeyWordMarkAnimationDuration = 500.milliseconds
    ).asScreen(presentationStyle = ScreenPresentationStyle.FullScreen)

  @Composable
  private fun ChooseAccountAccessScreenModel(
    chooseAccountAccessData: GettingStartedData,
    onSoftwareWalletCreated: (SoftwareAccount) -> Unit,
  ): ScreenModel =
    chooseAccountAccessUiStateMachine.model(
      props = ChooseAccountAccessUiProps(
        chooseAccountAccessData = chooseAccountAccessData,
        onSoftwareWalletCreated = onSoftwareWalletCreated
      )
    )

  @Composable
  private fun HomeScreenModel(accountData: ActiveFullAccountLoadedData): ScreenModel =
    homeUiStateMachine.model(
      props = HomeUiProps(
        account = accountData.account,
        lostHardwareRecoveryData = accountData.lostHardwareRecoveryData
      )
    )

  /**
   * Logs screen transitions as breadcrumbstate.
   */
  @Composable
  private fun LogScreenModelEffect(screenModel: ScreenModel) {
    DisposableEffect(screenModel.key) {
      logDebug(
        tag = "Screen" // This tag is used by a Datadog dashboard.
      ) { "${screenModel.body}" }

      onDispose { }
    }
  }

  /**
   * Track screen transitions as events.
   *
   * If there is an overlay (a bottom sheet), will use overlay's event info to track.
   * Otherwise, will use screen body's event info to track, if any.
   *
   * The idea is to track the screen that is most visible to the user. If an overlay gets
   * closed, the screen body will be tracked again.
   */
  @Composable
  private fun TrackScreenEvents(screenModel: ScreenModel) {
    val eventInfoToTrack = screenModel.eventInfoToTrack()
    eventInfoToTrack?.let {
      LaunchedEffect("track-screen-event", eventInfoToTrack) {
        eventTracker.track(eventInfoToTrack)
      }
    }

    val errorData = screenModel.errorDataToTrack()
    errorData?.let {
      LaunchedEffect("track-error-data", errorData) {
        errorData.log()
      }
    }
  }

  private fun ScreenModel.eventInfoToTrack(): EventTrackerScreenInfo? {
    // If there is an overlay (a bottom sheet), use it for the screen info.
    // Otherwise, use the body of the overall screen model.
    return when (bottomSheetModel) {
      null -> body.eventTrackerScreenInfo
      else -> bottomSheetModel?.body?.eventTrackerScreenInfo
    }
  }

  private fun ScreenModel.errorDataToTrack(): ErrorData? {
    // If there is an overlay (a bottom sheet), use it for the screen info.
    // Otherwise, use the body of the overall screen model.
    return when (bottomSheetModel) {
      null -> body.errorData()
      else -> bottomSheetModel?.body?.errorData()
    }
  }

  private fun BodyModel.errorData(): ErrorData? =
    when (this) {
      is FormBodyModel -> errorData
      else -> null
    }
}
