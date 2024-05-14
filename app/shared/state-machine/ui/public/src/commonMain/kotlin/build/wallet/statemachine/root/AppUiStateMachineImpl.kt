package build.wallet.statemachine.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.logging.LogLevel.Info
import build.wallet.logging.log
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.account.ChooseAccountAccessUiProps
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachine
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiProps
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiStateMachine
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.log
import build.wallet.statemachine.data.app.AppData.AppLoadedData
import build.wallet.statemachine.data.app.AppData.LoadingAppData
import build.wallet.statemachine.data.app.AppDataStateMachine
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.keybox.AccountData.CheckingActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CheckingCloudBackupData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CheckingRecoveryOrOnboarding
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CreatingFullAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CreatingLiteAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.RecoveringAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.RecoveringAccountWithEmergencyAccessKit
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.RecoveringLiteAccountData
import build.wallet.statemachine.data.sync.ElectrumServerData
import build.wallet.statemachine.dev.DebugMenuProps
import build.wallet.statemachine.dev.DebugMenuStateMachine
import build.wallet.statemachine.home.full.HomeUiProps
import build.wallet.statemachine.home.full.HomeUiStateMachine
import build.wallet.statemachine.home.lite.LiteHomeUiProps
import build.wallet.statemachine.home.lite.LiteHomeUiStateMachine
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiProps
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIOrigin
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachineProps
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
import build.wallet.time.Delayer
import build.wallet.worker.AppWorkerExecutor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
  private val appDataStateMachine: AppDataStateMachine,
  private val noLongerRecoveringUiStateMachine: NoLongerRecoveringUiStateMachine,
  private val someoneElseIsRecoveringUiStateMachine: SomeoneElseIsRecoveringUiStateMachine,
  private val gettingStartedRoutingStateMachine: GettingStartedRoutingStateMachine,
  private val createLiteAccountUiStateMachine: CreateLiteAccountUiStateMachine,
  private val liteAccountCloudBackupRestorationUiStateMachine:
    LiteAccountCloudBackupRestorationUiStateMachine,
  private val emergencyAccessKitRecoveryUiStateMachine: EmergencyAccessKitRecoveryUiStateMachine,
  private val authKeyRotationUiStateMachine: RotateAuthKeyUIStateMachine,
  private val appWorkerExecutor: AppWorkerExecutor,
) : AppUiStateMachine {
  /**
   * The last screen model emitted, if any.
   *
   * We keep track of this in order to only show [AppLoadingScreenModel] at the very beginning
   * of app startup, before other screens have been shown, and in order to show loading screens
   * back to back.
   */
  var previousScreenModel: ScreenModel? = null

  @Composable
  override fun model(props: Unit): ScreenModel {
    LaunchedEffect("execute-app-workers") {
      appWorkerExecutor.executeAll()
    }

    val appData = appDataStateMachine.model(Unit)

    var screenModel =
      when (appData) {
        is LoadingAppData -> AppLoadingScreenModel()
        is AppLoadedData -> AppLoadedDataScreenModel(appData)
      }

    LogScreenModelEffect(screenModel)
    TrackScreenEvents(screenModel)

    // If we are showing the Splash screen and then try to show a loading screen,
    // continue to show the Splash screen for more seamless app startup experience
    val shouldContinueToShowSplashScreen = remember(previousScreenModel, screenModel) {
      (previousScreenModel?.body is SplashBodyModel) &&
        (screenModel.body as? LoadingSuccessBodyModel)?.state == LoadingSuccessBodyModel.State.Loading
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
    if (isShowingDebugMenu && appData is AppLoadedData) {
      return debugMenuStateMachine.model(
        props = DebugMenuProps(
          accountData = appData.accountData,
          firmwareData = appData.firmwareData,
          onClose = { isShowingDebugMenu = false }
        )
      )
    }

    previousScreenModel = screenModel

    return when {
      appVariant.showDebugMenu ->
        screenModel.copy(onTwoFingerDoubleTap = { isShowingDebugMenu = true })
      else -> screenModel
    }
  }

  @Composable
  private fun AppLoadedDataScreenModel(appData: AppLoadedData): ScreenModel {
    // Keep track of when to show the "Welcome to Bitkey" screen.
    // We want to show it when we transition from NoActiveAccount -> HasActiveFullAccount
    var shouldShowWelcomeScreenWhenTransitionToActive by remember {
      mutableStateOf(false)
    }

    return when (val accountData = appData.accountData) {
      is CheckingActiveAccountData ->
        AppLoadingScreenModel()

      is NoActiveAccountData -> {
        shouldShowWelcomeScreenWhenTransitionToActive = true
        NoActiveAccountDataScreenModel(appData, accountData)
      }

      is HasActiveFullAccountData ->
        HasActiveFullAccountDataScreenModel(
          appData = appData,
          accountData = accountData,
          initialShouldShowWelcomeScreenWhenTransitionToActive = shouldShowWelcomeScreenWhenTransitionToActive
        )

      is AccountData.HasActiveLiteAccountData ->
        liteHomeUiStateMachine.model(
          props = LiteHomeUiProps(
            accountData = accountData,
            firmwareData = appData.firmwareData
          )
        )

      is AccountData.NoLongerRecoveringFullAccountData ->
        noLongerRecoveringUiStateMachine.model(
          props = NoLongerRecoveringUiProps(
            data = accountData.data
          )
        )

      is AccountData.SomeoneElseIsRecoveringFullAccountData ->
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
    appData: AppLoadedData,
    accountData: NoActiveAccountData,
  ): ScreenModel {
    return when (accountData) {
      is CheckingRecoveryOrOnboarding -> AppLoadingScreenModel()

      is GettingStartedData ->
        ChooseAccountAccessScreenModel(
          chooseAccountAccessData = accountData,
          firmwareData = appData.firmwareData
        )

      is RecoveringAccountData ->
        lostAppRecoveryUiStateMachine.model(
          LostAppRecoveryUiProps(
            recoveryData = accountData.lostAppRecoveryData,
            fullAccountConfig = accountData.templateFullAccountConfig
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
            fullAccountConfig = accountData.templateFullAccountConfig,
            onExit = accountData.onExit
          )
        )

      is CreatingFullAccountData ->
        createAccountUiStateMachine.model(
          props = CreateAccountUiProps(
            createFullAccountData = accountData.createFullAccountData,
            isHardwareFake = accountData.templateFullAccountConfig.isHardwareFake
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
            accountConfig = LiteAccountConfig(
              bitcoinNetworkType = accountData.templateFullAccountConfig.bitcoinNetworkType,
              f8eEnvironment = accountData.templateFullAccountConfig.f8eEnvironment,
              isTestAccount = accountData.templateFullAccountConfig.isTestAccount,
              isUsingSocRecFakes = accountData.templateFullAccountConfig.isUsingSocRecFakes
            ),
            inviteCode = accountData.inviteCode,
            onAccountCreated = accountData.onAccountCreated,
            // If this flow was reached via invite code, show the introduction screen.
            showBeTrustedContactIntroduction = accountData.inviteCode != null
          )
        )
    }
  }

  @Composable
  private fun HasActiveFullAccountDataScreenModel(
    appData: AppLoadedData,
    accountData: HasActiveFullAccountData,
    initialShouldShowWelcomeScreenWhenTransitionToActive: Boolean,
  ): ScreenModel {
    var shouldShowWelcomeScreenWhenTransitionToActive by remember {
      mutableStateOf(initialShouldShowWelcomeScreenWhenTransitionToActive)
    }

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
          HomeScreenModel(
            accountData = accountData,
            electrumServerData = appData.electrumServerData,
            firmwareData = appData.firmwareData
          )
        }

      is HasActiveFullAccountData.LoadingActiveFullAccountData ->
        AppLoadingScreenModel()

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
            (previousScreenModel.body as? LoadingSuccessBodyModel)?.state == LoadingSuccessBodyModel.State.Loading
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
  private fun SplashScreenModel(): ScreenModel {
    return SplashBodyModel(
      bitkeyWordMarkAnimationDelay = 700.milliseconds,
      bitkeyWordMarkAnimationDuration = 500.milliseconds
    ).asScreen(presentationStyle = ScreenPresentationStyle.FullScreen)
  }

  @Composable
  private fun ChooseAccountAccessScreenModel(
    chooseAccountAccessData: GettingStartedData,
    firmwareData: FirmwareData,
  ): ScreenModel =
    chooseAccountAccessUiStateMachine.model(
      props = ChooseAccountAccessUiProps(
        chooseAccountAccessData = chooseAccountAccessData,
        firmwareData = firmwareData
      )
    )

  @Composable
  private fun HomeScreenModel(
    accountData: ActiveFullAccountLoadedData,
    electrumServerData: ElectrumServerData,
    firmwareData: FirmwareData,
  ): ScreenModel =
    homeUiStateMachine.model(
      props = HomeUiProps(
        accountData = accountData,
        electrumServerData = electrumServerData,
        firmwareData = firmwareData
      )
    )

  /**
   * Logs screen transitions as breadcrumbstate.
   */
  @Composable
  private fun LogScreenModelEffect(screenModel: ScreenModel) {
    DisposableEffect(screenModel.key) {
      log(
        level = Info,
        tag = "Screen"
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

  private fun BodyModel.errorData(): ErrorData? {
    return when (this) {
      is FormBodyModel -> errorData
      else -> null
    }
  }
}
