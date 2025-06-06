package build.wallet.statemachine.root

import androidx.compose.runtime.*
import bitkey.datadog.DatadogRumMonitor
import bitkey.ui.framework.NavigatorPresenter
import bitkey.ui.statemachine.interstitial.InterstitialUiProps
import bitkey.ui.statemachine.interstitial.InterstitialUiStateMachine
import build.wallet.account.AccountService
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bootstrap.AppState
import build.wallet.bootstrap.LoadAppService
import build.wallet.cloud.backup.CloudBackup
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inappsecurity.BiometricAuthService
import build.wallet.logging.logInfo
import build.wallet.onboarding.CreateFullAccountContext
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform
import build.wallet.router.Route
import build.wallet.router.Router
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
import build.wallet.statemachine.data.keybox.AccountDataProps
import build.wallet.statemachine.data.keybox.AccountDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.GeneratingNewAppKeysData
import build.wallet.statemachine.dev.DebugMenuScreen
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
import build.wallet.statemachine.settings.showDebugMenu
import build.wallet.statemachine.start.GettingStartedRoutingProps
import build.wallet.statemachine.start.GettingStartedRoutingStateMachine
import build.wallet.worker.AppWorkerExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@BitkeyInject(ActivityScope::class)
class AppUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val navigatorPresenter: NavigatorPresenter,
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
  private val appWorkerExecutor: AppWorkerExecutor,
  private val biometricAuthService: BiometricAuthService,
  private val biometricPromptUiStateMachine: BiometricPromptUiStateMachine,
  private val accountService: AccountService,
  private val datadogRumMonitor: DatadogRumMonitor,
  private val splashScreenDelay: SplashScreenDelay,
  private val welcomeToBitkeyScreenDuration: WelcomeToBitkeyScreenDuration,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val interstitialUiStateMachine: InterstitialUiStateMachine,
) : AppUiStateMachine {
  /**
   * The last screen model emitted, if any.
   *
   * We keep track of this in order to only show [AppLoadingScreenModel] at the very beginning
   * of app startup, before other screens have been shown, and in order to show loading screens
   * back to back.
   */
  private var previousScreenModel: ScreenModel? = null

  @Suppress("CyclomaticComplexMethod")
  @Composable
  override fun model(props: Unit): ScreenModel {
    LaunchedEffect("execute-app-workers") {
      appWorkerExecutor.executeAll()
    }
    var accountData by remember { mutableStateOf<AccountData?>(null) }
    val deviceInfo = remember { deviceInfoProvider.getDeviceInfo() }

    val appState by produceState<AppState?>(initialValue = null) {
      value = loadAppService.loadAppState()
    }

    val scope = rememberStableCoroutineScope()

    var isAnimatingSplashScreen by remember { mutableStateOf(previousScreenModel == null) }

    var uiState by remember(appState, isAnimatingSplashScreen) {
      if (isAnimatingSplashScreen) {
        mutableStateOf<State>(State.ShowingSplashScreen)
      } else {
        when (val currentState = appState) {
          is AppState.HasActiveFullAccount -> mutableStateOf<State>(
            State.ViewingFullAccount(
              account = currentState.account,
              isNewlyCreatedAccount = false
            )
          )
          is AppState.HasActiveSoftwareAccount -> mutableStateOf<State>(
            State.ViewingSoftwareAccount(
              account = currentState.account,
              isNewlyCreatedAccount = false
            )
          )
          is AppState.HasActiveLiteAccount -> mutableStateOf<State>(
            State.ViewingLiteAccount(currentState.account)
          )
          is AppState.OnboardingFullAccount -> mutableStateOf<State>(
            State.OnboardingFullAccount(
              account = currentState.account
            )
          )
          is AppState.LiteAccountOnboardingToFullAccount -> mutableStateOf<State>(
            State.LiteAccountOnboardingToFullAccount(
              activeAccount = currentState.activeAccount,
              onboardingAccount = currentState.onboardingAccount
            )
          )
          AppState.Undetermined -> mutableStateOf<State>(State.RenderingViaAccountData(false))
          null -> mutableStateOf<State>(State.LoadingApp)
        }
      }
    }

    var screenModel = when (val state = uiState) {
      State.ShowingSplashScreen -> {
        LaunchedEffect("show-splash-for-animation") {
          delay(splashScreenDelay.value)
          isAnimatingSplashScreen = false
        }
        SplashScreenModel()
      }
      State.LoadingApp -> AppLoadingScreenModel()
      is State.ViewingLiteAccount -> liteHomeUiStateMachine.model(
        props = LiteHomeUiProps(
          account = state.account,
          onUpgradeComplete = { fullAccount ->
            uiState = State.ViewingFullAccount(
              account = fullAccount,
              isNewlyCreatedAccount = false
            )
          }
        )
      )
      is State.RenderingViaAccountData -> accountDataStateMachine.model(
        props = AccountDataProps {
          uiState = State.ViewingLiteAccount(it)
        }
      ).let {
        accountData = it
        AppLoadedDataScreenModel(
          accountData = it,
          onSoftwareWalletCreated = { swAccount ->
            uiState = State.ViewingSoftwareAccount(
              account = swAccount,
              isNewlyCreatedAccount = true
            )
          },
          onStartLiteAccountRecovery = { cloudBackup ->
            uiState = State.RecoveringLiteAccount(cloudBackup)
          },
          onStartLiteAccountCreation = { inviteCode, startIntent ->
            uiState = State.CreatingLiteAccount(inviteCode, startIntent)
          },
          onCreateFullAccount = {
            uiState = State.CreatingFullAccount
          },
          isNewlyCreatedAccount = state.isNewlyCreatedAccount
        )
      }
      is State.ViewingFullAccount -> {
        var shouldShowWelcomeScreenWhenTransitionToActive by remember {
          mutableStateOf(state.isNewlyCreatedAccount)
        }

        // If we just created the full account, we want to briefly show a welcome screen.
        if (shouldShowWelcomeScreenWhenTransitionToActive) {
          LaunchedEffect("show-welcome-screen") {
            delay(welcomeToBitkeyScreenDuration.value)
            shouldShowWelcomeScreenWhenTransitionToActive = false
          }
          LoadingSuccessBodyModel(
            id = null,
            message = "Welcome to Bitkey",
            state = LoadingSuccessBodyModel.State.Success
          ).asRootScreen()
        } else {
          accountDataStateMachine.model(
            props = AccountDataProps {
              uiState = State.ViewingLiteAccount(it)
            }
          ).let {
            accountData = it
            AppLoadedDataScreenModel(
              accountData = it,
              onSoftwareWalletCreated = { swAccount ->
                uiState = State.ViewingSoftwareAccount(
                  account = swAccount,
                  isNewlyCreatedAccount = true
                )
              },
              onStartLiteAccountRecovery = { cloudBackup ->
                uiState = State.RecoveringLiteAccount(cloudBackup)
              },
              onStartLiteAccountCreation = { inviteCode, startIntent ->
                uiState = State.CreatingLiteAccount(inviteCode, startIntent)
              },
              onCreateFullAccount = {
                uiState = State.CreatingFullAccount
              },
              isNewlyCreatedAccount = state.isNewlyCreatedAccount
            )
          }
        }
      }
      is State.OnboardingFullAccount -> createAccountUiStateMachine.model(
        props = CreateAccountUiProps(
          context = CreateFullAccountContext.NewFullAccount,
          fullAccount = state.account,
          rollback = {
            /*
             * At this point there is no rollback since the account has been created in f8e and the
             * customer is completing the onboarding process
             */
          },
          onOnboardingComplete = {
            uiState = State.ViewingFullAccount(
              account = state.account,
              isNewlyCreatedAccount = true
            )
          }
        )
      )
      is State.ViewingSoftwareAccount -> HasActiveSoftwareAccountScreenModel(
        account = state.account,
        isNewlyCreatedAccount = state.isNewlyCreatedAccount
      )
      is State.RecoveringLiteAccount -> liteAccountCloudBackupRestorationUiStateMachine.model(
        props = LiteAccountCloudBackupRestorationUiProps(
          cloudBackup = state.cloudBackup,
          onLiteAccountRestored = { account ->
            scope.launch {
              accountService.setActiveAccount(account)
              uiState = State.ViewingLiteAccount(account)
            }
          },
          onExit = {
            uiState = State.RenderingViaAccountData(isNewlyCreatedAccount = false)
          }
        )
      )
      is State.CreatingLiteAccount -> createLiteAccountUiStateMachine.model(
        props = CreateLiteAccountUiProps(
          inviteCode = state.inviteCode,
          onAccountCreated = { account ->
            scope.launch {
              accountService.setActiveAccount(account)
              uiState = when (account) {
                is FullAccount -> State.ViewingFullAccount(account, isNewlyCreatedAccount = false)
                is LiteAccount -> State.ViewingLiteAccount(account)
                is SoftwareAccount -> State.ViewingSoftwareAccount(account, isNewlyCreatedAccount = false)
                else -> error("Unexpected account type: $account")
              }
            }
          },
          onBack = {
            uiState = State.RenderingViaAccountData(
              isNewlyCreatedAccount = false
            )
          },
          showBeTrustedContactIntroduction = state.inviteCode != null && state.startIntent == StartIntent.BeTrustedContact
        )
      )
      is State.LiteAccountOnboardingToFullAccount -> createAccountUiStateMachine.model(
        props = CreateAccountUiProps(
          context = CreateFullAccountContext.LiteToFullAccountUpgrade(state.activeAccount),
          fullAccount = state.onboardingAccount,
          rollback = {
            /*
             * At this point there is no rollback since the account has been created in f8e and the
             * customer is completing the onboarding process
             */
          },
          onOnboardingComplete = {
            uiState = State.ViewingFullAccount(
              account = it,
              isNewlyCreatedAccount = true
            )
          }
        )
      )
      State.CreatingFullAccount -> createAccountUiStateMachine.model(
        props = CreateAccountUiProps(
          context = CreateFullAccountContext.NewFullAccount,
          fullAccount = null,
          rollback = {
            uiState = State.RenderingViaAccountData(
              isNewlyCreatedAccount = false
            )
          },
          onOnboardingComplete = { account ->
            scope.launch {
              accountService.setActiveAccount(account)
              uiState = State.ViewingFullAccount(
                account = account,
                isNewlyCreatedAccount = true
              )
            }
          }
        )
      )
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

    // Set up an app-wide handler to show the debug menu.
    var isShowingDebugMenu by remember { mutableStateOf(false) }
    if (isShowingDebugMenu) {
      accountData?.let {
        return navigatorPresenter.model(
          initialScreen = DebugMenuScreen,
          onExit = { isShowingDebugMenu = false }
        )
      }
    }

    val targetModel = when {
      deviceInfo.devicePlatform == DevicePlatform.IOS && screenModel.platformNfcScreen -> {
        // If incoming model displays an NFC screen that is handled
        // by native iOS UI, keep displaying the previous screen model
        // to maintain overlay effect.
        previousScreenModel ?: screenModel
      }
      else -> {
        previousScreenModel = screenModel
        screenModel
      }
    }
    return when {
      appVariant.showDebugMenu ->
        targetModel.copy(onTwoFingerDoubleTap = { isShowingDebugMenu = true })
      else -> targetModel
    }
  }

  @Composable
  private fun HasActiveSoftwareAccountScreenModel(
    account: SoftwareAccount,
    isNewlyCreatedAccount: Boolean,
  ): ScreenModel {
    var shouldShowWelcomeScreenWhenTransitionToActive by remember {
      mutableStateOf(isNewlyCreatedAccount)
    }

    // If we just created the software account, we want to briefly show a welcome screen.
    return if (shouldShowWelcomeScreenWhenTransitionToActive) {
      LaunchedEffect("show-welcome-screen") {
        delay(welcomeToBitkeyScreenDuration.value)
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
    onStartLiteAccountRecovery: (CloudBackup) -> Unit,
    onStartLiteAccountCreation: (String?, StartIntent) -> Unit,
    onCreateFullAccount: () -> Unit,
    isNewlyCreatedAccount: Boolean,
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
        NoActiveAccountDataScreenModel(
          accountData,
          onSoftwareWalletCreated,
          onStartLiteAccountRecovery,
          onStartLiteAccountCreation,
          onCreateFullAccount
        )
      }

      is HasActiveFullAccountData ->
        HasActiveFullAccountDataScreenModel(
          accountData = accountData,
          isNewlyCreatedAccount = isNewlyCreatedAccount
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
            fullAccountId = accountData.fullAccountId
          )
        )
    }
  }

  @Composable
  private fun NoActiveAccountDataScreenModel(
    accountData: NoActiveAccountData,
    onSoftwareWalletCreated: (SoftwareAccount) -> Unit,
    onStartLiteAccountRecovery: (CloudBackup) -> Unit,
    onStartLiteAccountCreation: (String?, StartIntent) -> Unit,
    onCreateFullAccount: () -> Unit,
  ): ScreenModel {
    return when (accountData) {
      is CheckingRecovery -> AppLoadingScreenModel()

      is GettingStartedData ->
        ChooseAccountAccessScreenModel(
          chooseAccountAccessData = accountData,
          onSoftwareWalletCreated = onSoftwareWalletCreated,
          onCreateFullAccount = onCreateFullAccount
        )

      is RecoveringAccountData ->
        lostAppRecoveryUiStateMachine.model(
          LostAppRecoveryUiProps(
            recoveryData = accountData.lostAppRecoveryData
          )
        )

      is RecoveringAccountWithEmergencyAccessKit ->
        emergencyAccessKitRecoveryUiStateMachine.model(
          EmergencyAccessKitRecoveryUiStateMachineProps(
            onExit = accountData.onExit
          )
        )

      is CheckingCloudBackupData ->
        gettingStartedRoutingStateMachine.model(
          GettingStartedRoutingProps(
            startIntent = accountData.intent,
            inviteCode = accountData.inviteCode,
            onStartLiteAccountRecovery = { cloudBackup ->
              if (accountData.inviteCode != null) {
                Router.route = Route.TrustedContactInvite(accountData.inviteCode!!)
              }
              onStartLiteAccountRecovery(cloudBackup)
            },
            onStartCloudRecovery = accountData.onStartCloudRecovery,
            onStartLostAppRecovery = accountData.onStartLostAppRecovery,
            onStartLiteAccountCreation = {
              onStartLiteAccountCreation(it, accountData.intent)
            },
            onImportEmergencyAccessKit = accountData.onImportEmergencyAccessKit,
            onExit = accountData.onExit
          )
        )
    }
  }

  @Composable
  private fun HasActiveFullAccountDataScreenModel(
    accountData: HasActiveFullAccountData,
    isNewlyCreatedAccount: Boolean,
  ): ScreenModel {
    val shouldPromptForAuth by remember { biometricAuthService.isBiometricAuthRequired() }
      .collectAsState()

    return when (accountData) {
      is ActiveFullAccountLoadedData -> {
        val homeScreenModel = HomeScreenModel(
          accountData = accountData
        )

        biometricPromptUiStateMachine.model(
          props = BiometricPromptProps(
            shouldPromptForAuth = shouldPromptForAuth
          )
        ) ?: interstitialUiStateMachine.model(
          props = InterstitialUiProps(
            account = accountData.account,
            isComingFromOnboarding = isNewlyCreatedAccount
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
    onCreateFullAccount: () -> Unit,
  ): ScreenModel =
    chooseAccountAccessUiStateMachine.model(
      props = ChooseAccountAccessUiProps(
        chooseAccountAccessData = chooseAccountAccessData,
        onSoftwareWalletCreated = onSoftwareWalletCreated,
        onCreateFullAccount = onCreateFullAccount
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
      logInfo(
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
      DisposableEffect("track-screen-event", eventInfoToTrack) {
        eventTracker.track(eventInfoToTrack)

        // Log the screens as RUM Views. This allows us to better understand user sessions, such as
        // through funnels.
        if (eventInfoToTrack.eventTrackerShouldTrack) {
          datadogRumMonitor.startView(key = eventInfoToTrack.screenId)
        }

        onDispose {
          if (eventInfoToTrack.eventTrackerShouldTrack) {
            datadogRumMonitor.stopView(key = eventInfoToTrack.screenId)
          }
        }
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

private sealed interface State {
  data object ShowingSplashScreen : State

  data object LoadingApp : State

  data class OnboardingFullAccount(
    val account: FullAccount,
  ) : State

  data class LiteAccountOnboardingToFullAccount(
    val activeAccount: LiteAccount,
    val onboardingAccount: FullAccount,
  ) : State

  data class ViewingFullAccount(
    val account: FullAccount,
    val isNewlyCreatedAccount: Boolean,
  ) : State

  data class ViewingSoftwareAccount(
    val account: SoftwareAccount,
    val isNewlyCreatedAccount: Boolean,
  ) : State

  data class ViewingLiteAccount(
    val account: LiteAccount,
  ) : State

  data class RenderingViaAccountData(val isNewlyCreatedAccount: Boolean) : State

  data class RecoveringLiteAccount(val cloudBackup: CloudBackup) : State

  data class CreatingLiteAccount(val inviteCode: String?, val startIntent: StartIntent) : State

  data object CreatingFullAccount : State
}
