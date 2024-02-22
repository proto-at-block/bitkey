package build.wallet.statemachine.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.emergencyaccesskit.EmergencyAccessKitAssociation
import build.wallet.f8e.debug.NetworkingDebugConfigRepository
import build.wallet.logging.LogLevel.Info
import build.wallet.logging.log
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.account.ChooseAccountAccessUiProps
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachine
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiProps
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiStateMachine
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SplashBodyModel
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
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiStateMachine
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiStateMachine
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRecoveryUiStateMachine
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRecoveryUiStateMachineProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachine
import build.wallet.statemachine.start.GettingStartedRoutingProps
import build.wallet.statemachine.start.GettingStartedRoutingStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AppUiStateMachineImpl(
  private val appVariant: AppVariant,
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
  private val networkingDebugConfigRepository: NetworkingDebugConfigRepository,
  private val appCoroutineScope: CoroutineScope,
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
    val appData = appDataStateMachine.model(Unit)

    var screenModel =
      when (appData) {
        is AppLoadedData -> {
          when (val accountData = appData.accountData) {
            is CheckingActiveAccountData -> AppLoadingScreenModel()

            is NoActiveAccountData -> {
              when (accountData) {
                is CheckingRecoveryOrOnboarding -> AppLoadingScreenModel()

                is GettingStartedData ->
                  ChooseAccountAccessScreenModel(
                    chooseAccountAccessData = accountData,
                    firmwareData = appData.firmwareData,
                    eakAssociation = appData.eakAssociation
                  )

                is RecoveringAccountData ->
                  lostAppRecoveryUiStateMachine.model(
                    LostAppRecoveryUiProps(
                      recoveryData = accountData.lostAppRecoveryData,
                      keyboxConfig = accountData.templateKeyboxConfig,
                      fiatCurrency = appData.currencyPreferenceData.fiatCurrencyPreference,
                      eakAssociation = appData.eakAssociation
                    )
                  )

                is RecoveringLiteAccountData ->
                  liteAccountCloudBackupRestorationUiStateMachine.model(
                    props =
                      LiteAccountCloudBackupRestorationUiProps(
                        cloudBackup = accountData.cloudBackup,
                        onLiteAccountRestored = accountData.onAccountCreated,
                        onExit = accountData.onExit
                      )
                  )

                is RecoveringAccountWithEmergencyAccessKit ->
                  emergencyAccessKitRecoveryUiStateMachine.model(
                    EmergencyAccessKitRecoveryUiStateMachineProps(
                      keyboxConfig = accountData.templateKeyboxConfig,
                      onExit = accountData.onExit
                    )
                  )

                is CreatingFullAccountData ->
                  createAccountUiStateMachine.model(
                    props =
                      CreateAccountUiProps(
                        createFullAccountData = accountData.createFullAccountData,
                        isHardwareFake = accountData.templateKeyboxConfig.isHardwareFake
                      )
                  )

                is CheckingCloudBackupData ->
                  gettingStartedRoutingStateMachine.model(
                    GettingStartedRoutingProps(
                      startIntent = accountData.intent,
                      eakAssociation = appData.eakAssociation,
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
                    props =
                      CreateLiteAccountUiProps(
                        onBack = accountData.onRollback,
                        accountConfig =
                          LiteAccountConfig(
                            bitcoinNetworkType = accountData.templateKeyboxConfig.networkType,
                            f8eEnvironment = accountData.templateKeyboxConfig.f8eEnvironment,
                            isTestAccount = accountData.templateKeyboxConfig.isTestAccount,
                            isUsingSocRecFakes = accountData.templateKeyboxConfig.isUsingSocRecFakes
                          ),
                        inviteCode = accountData.inviteCode,
                        onAccountCreated = accountData.onAccountCreated
                      )
                  )
              }
            }

            is HasActiveFullAccountData -> {
              when (accountData) {
                is ActiveFullAccountLoadedData ->
                  HomeScreenModel(
                    accountData = accountData,
                    electrumServerData = appData.electrumServerData,
                    firmwareData = appData.firmwareData,
                    currencyPreferenceData = appData.currencyPreferenceData
                  )

                is HasActiveFullAccountData.LoadingActiveFullAccountData ->
                  AppLoadingScreenModel()
              }
            }

            is AccountData.HasActiveLiteAccountData ->
              liteHomeUiStateMachine.model(
                props =
                  LiteHomeUiProps(
                    accountData = accountData,
                    currencyPreferenceData = appData.currencyPreferenceData,
                    firmwareData = appData.firmwareData
                  )
              )

            is AccountData.NoLongerRecoveringFullAccountData ->
              noLongerRecoveringUiStateMachine.model(
                props =
                  NoLongerRecoveringUiProps(
                    data = accountData.data
                  )
              )

            is AccountData.SomeoneElseIsRecoveringFullAccountData ->
              someoneElseIsRecoveringUiStateMachine.model(
                props =
                  SomeoneElseIsRecoveringUiProps(
                    data = accountData.data,
                    keyboxConfig = accountData.keyboxConfig,
                    fullAccountId = accountData.fullAccountId
                  )
              )
          }
        }

        LoadingAppData -> AppLoadingScreenModel()
      }

    LogScreenModelEffect(screenModel)
    TrackScreenEvents(screenModel)

    // If we are showing the Splash screen and then try to show a loading screen,
    // continue to show the Splash screen for more seamless app startup experience
    val shouldContinueToShowSplashScreen =
      remember(previousScreenModel, screenModel) {
        (previousScreenModel?.body is SplashBodyModel) &&
          (screenModel.body is LoadingBodyModel)
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
        delay(2.seconds)
        isAnimatingSplashScreen = false
      }
      // Early return the splash screen model here while we let it animate.
      return SplashScreenModel()
    }

    // Set up an app-wide handler to show the debug menu.
    var isShowingDebugMenu by remember { mutableStateOf(false) }
    if (isShowingDebugMenu && appData is AppLoadedData) {
      return debugMenuStateMachine.model(
        props =
          DebugMenuProps(
            accountData = appData.accountData,
            firmwareData = appData.firmwareData,
            onClose = { isShowingDebugMenu = false }
          )
      )
    }

    previousScreenModel = screenModel

    // Only add debug functionality in non-customer builds
    return when (appVariant) {
      AppVariant.Beta, AppVariant.Customer, AppVariant.Emergency ->
        screenModel

      AppVariant.Team, AppVariant.Development ->
        screenModel.copy(
          onTwoFingerDoubleTap = { isShowingDebugMenu = true },
          onTwoFingerTripleTap = {
            appCoroutineScope.launch {
              networkingDebugConfigRepository.setFailF8eRequests(
                value = !networkingDebugConfigRepository.config.value.failF8eRequests
              )
            }
          }
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
        when (previousScreenModel.body) {
          is SplashBodyModel, is LoadingBodyModel -> previousScreenModel
          else ->
            LoadingBodyModel(
              id = GeneralEventTrackerScreenId.LOADING_APP
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
    eakAssociation: EmergencyAccessKitAssociation,
  ): ScreenModel =
    chooseAccountAccessUiStateMachine.model(
      props =
        ChooseAccountAccessUiProps(
          chooseAccountAccessData = chooseAccountAccessData,
          firmwareData = firmwareData,
          eakAssociation = eakAssociation
        )
    )

  @Composable
  private fun HomeScreenModel(
    accountData: ActiveFullAccountLoadedData,
    electrumServerData: ElectrumServerData,
    firmwareData: FirmwareData,
    currencyPreferenceData: CurrencyPreferenceData,
  ): ScreenModel =
    homeUiStateMachine.model(
      props =
        HomeUiProps(
          accountData = accountData,
          electrumServerData = electrumServerData,
          firmwareData = firmwareData,
          currencyPreferenceData = currencyPreferenceData
        )
    )

  /**
   * Logs screen transitions as breadcrumbstate.
   */
  @Composable
  private fun LogScreenModelEffect(screenModel: ScreenModel) {
    DisposableEffect(screenModel.body.key) {
      log(
        level = Info,
        tag = "Screen"
      ) { "${screenModel.body}" }

      onDispose { }
    }
  }

  /**
   * Track screen transitions as events.
   */
  @Composable
  private fun TrackScreenEvents(screenModel: ScreenModel) {
    val bottomSheet = screenModel.bottomSheetModel

    DisposableEffect(screenModel.body.eventTrackerScreenInfo, bottomSheet) {
      trackScreenId(bottomSheet, screenModel)
      onDispose {}
    }
  }

  private fun trackScreenId(
    bottomSheet: SheetModel?,
    screenModel: ScreenModel,
  ) {
    // If there is an overlay (a bottom sheet), use it for the screen info.
    // Otherwise, use the body of the overall screen model.
    val eventTrackerScreenInfo =
      when (bottomSheet) {
        null -> screenModel.body.eventTrackerScreenInfo
        else -> bottomSheet.body.eventTrackerScreenInfo
      }

    // Only track the screen event if the specified info is nonnull.
    when (eventTrackerScreenInfo) {
      null -> Unit
      else -> eventTracker.track(eventTrackerScreenInfo = eventTrackerScreenInfo)
    }
  }
}
