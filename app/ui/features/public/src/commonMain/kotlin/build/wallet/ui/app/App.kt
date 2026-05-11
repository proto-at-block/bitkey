package build.wallet.ui.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState.Visible
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId
import build.wallet.feature.FeatureFlagValue
import build.wallet.platform.device.DeviceInfo
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.sensor.Accelerometer
import build.wallet.statemachine.account.create.full.hardware.CompleteTwoTapBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.ScreenPresentationStyle.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.fwup.FwupNfcBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.HardwareConfirmationResultBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationCanceledScreenModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel

import build.wallet.ui.components.screen.*
import build.wallet.ui.compose.LocalHaptics
import build.wallet.ui.model.UiModelContentScreen
import build.wallet.ui.theme.*
import build.wallet.ui.tokens.backgroundColor
import cafe.adriel.voyager.core.stack.StackEvent.*
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransitionContent
import kotlinx.coroutines.flow.StateFlow
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

/**
 * Top-level UI of the app.
 */
@Composable
fun App(
  model: ScreenModel,
  deviceInfo: DeviceInfo,
  accelerometer: Accelerometer?,
  themePreferenceService: ThemePreferenceService?,
  haptics: Haptics?,
  designSystemUpdatesEnabled: StateFlow<FeatureFlagValue.BooleanFlag>? = null,
) {
  var previousPresentationStyle by remember {
    mutableStateOf(model.presentationStyle)
  }

  var currentPresentationStyle by remember {
    mutableStateOf(model.presentationStyle)
  }

  val currentSystemTheme = systemTheme()

  // Whenever the system theme changes, update our themePreferenceService
  LaunchedEffect(currentSystemTheme) {
    themePreferenceService?.setSystemTheme(currentSystemTheme)
  }

  // Collect the theme from the service, defaulting to the system theme
  val appTheme by themePreferenceService?.theme()?.collectAsState(initial = currentSystemTheme)
    ?: remember { mutableStateOf(currentSystemTheme) }

  // Collect the design system updates flag, defaulting to false
  val isDesignSystemV2Enabled by designSystemUpdatesEnabled?.collectAsState()
    ?: remember { mutableStateOf(FeatureFlagValue.BooleanFlag(false)) }

  CompositionLocalProvider(
    LocalDeviceInfo provides deviceInfo,
    LocalAccelerometer provides accelerometer,
    LocalTheme provides appTheme,
    LocalHaptics provides haptics,
    LocalDesignSystemUpdatesEnabled provides isDesignSystemV2Enabled.value
  ) {
    WalletTheme {
      val backdropTheme =
        effectiveTheme(appTheme = appTheme, screenThemePreference = model.themePreference)
      val rootBackgroundColor = if (usesBlackFullscreenBackground(
          bodyModel = model.body,
          isDesignSystemV2Enabled = isDesignSystemV2Enabled.value
        )
      ) {
        Color.Black
      } else {
        backdropTheme.backgroundColor(designSystemUpdatesEnabled = isDesignSystemV2Enabled.value)
      }
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(rootBackgroundColor)
      ) {
        Navigator(
          screen = UiModelContentScreen(model = model),
          onBackPressed = { screen ->
            val bodyModel = (screen as UiModelContentScreen).model.body
            // Special case: Security Hub uses onHomeTabClick instead of onBack
            if (bodyModel is SecurityHubBodyModel) {
              bodyModel.onHomeTabClick()
            } else {
              // Let the BodyModel handle the back action explicitly
              bodyModel.onBack?.invoke()
            }
            // Never let the Navigator handle the back press
            false
          }
        ) { navigator ->
          // this effect is responsible for translating the stream of ScreenModels into a backstack
          NavigatorModelEffect(
            navigator = navigator,
            model = model,
            updatePresentationStyle = { screenPresentationStyle ->
              previousPresentationStyle = currentPresentationStyle
              currentPresentationStyle = screenPresentationStyle
            }
          )
          // This composable wraps the current screen and applies the appropriate animation
          BitkeyTransition(
            navigator = navigator,
            previousPresentationStyle = previousPresentationStyle,
            currentPresentationStyle = currentPresentationStyle
          ) { screen ->
            screen.Content()
          }
        }
      }
    }
  }
}

internal fun effectiveTheme(
  appTheme: Theme,
  screenThemePreference: ThemePreference?,
): Theme =
  when (screenThemePreference) {
    is ThemePreference.Manual -> screenThemePreference.value
    ThemePreference.System, null -> appTheme
  }

/**
 * This effect is responsible for translating the stream of [ScreenModel]s into a backstack for via
 * the [Navigator].
 *
 * @param navigator - the voyager [Navigator] to use for handling screen navigation.
 * @param model - the current [ScreenModel] to render.
 * @param updatePresentationStyle - callback to update the presentation style of the current screen.
 */
@Composable
private fun NavigatorModelEffect(
  navigator: Navigator,
  model: ScreenModel,
  updatePresentationStyle: (ScreenPresentationStyle) -> Unit,
) {
  LaunchedEffect(model) {
    if (navigator.shouldReplaceModel(model)) {
      // Don't perform any animation if the model should just be directly replaced.
      navigator.currentScreen().model = model
    } else if (navigator.lastItem.key != model.key) {
      // The new model to present has a different key than the current item
      // Check if we've seen the screen before
      if (navigator.items.any { it.key == model.key }) {
        // Pop back to the screen with the first version of the model we saw
        // and update the model to the current given one
        navigator.popUntil { it.key == model.key }
        navigator.currentScreen().model = model
        updatePresentationStyle(model.presentationStyle)
      } else {
        // Push the new model
        navigator.push(item = UiModelContentScreen(model = model))
        updatePresentationStyle(model.presentationStyle)
      }
    } else {
      // when the screen model is updating and not navigating to a different screen, we need to
      // update the screen model of the current item in the stack
      navigator.currentScreen().model = model
    }
  }
}

/**
 * A custom Voyager transition that interprets screen models and the backstack to show the appropriate
 * transition
 *
 * @param navigator - a reference to the navigator to use for navigation
 * @param previousPresentationStyle - the presentation style for the previous screen model
 * @param currentPresentationStyle - the presentation style for the current screen model
 * @param modifier - the modifier to apply to the transition
 * @param content - the content to be rendered
 */
@Composable
private fun BitkeyTransition(
  navigator: Navigator,
  previousPresentationStyle: ScreenPresentationStyle,
  currentPresentationStyle: ScreenPresentationStyle,
  modifier: Modifier = Modifier,
  content: ScreenTransitionContent,
) {
  // The screen density is used to calculate the appropriate animation distance based on the dimensions
  // of the screen. This is passed to the animation retrieval functions which returns the appropriate
  // animation
  val density = LocalDensity.current
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  val transitionSpec: AnimatedContentTransitionScope<VoyagerScreen>.() -> ContentTransform = {
    val fromModel = (initialState as UiModelContentScreen).model
    val toModel = (targetState as UiModelContentScreen).model
    val hardwareConfirmationTransition =
      hardwareConfirmationContentTransform(
        lastEvent = navigator.lastEvent,
        fromModel = fromModel,
        toModel = toModel
      )
    val nfcTransition =
      nfcContentTransform(
        lastEvent = navigator.lastEvent,
        fromModel = fromModel,
        toModel = toModel
      )

    when {
      hardwareConfirmationTransition != null -> hardwareConfirmationTransition
      nfcTransition != null -> nfcTransition
      shouldSkipTransitionAnimation(
        lastEvent = navigator.lastEvent,
        fromModel = fromModel,
        toModel = toModel,
        isDesignSystemV2Enabled = isDesignSystemV2Enabled
      ) -> NoAnimation
      navigator.lastEvent == Pop ->
        navigator.popContentTransform(
          previousPresentationStyle,
          currentPresentationStyle,
          density
        )
      navigator.lastEvent == Push ->
        navigator.pushContentTransform(
          previousPresentationStyle,
          currentPresentationStyle,
          density
        )
      else -> NoAnimation
    }
  }

  AnimatedContent(
    targetState = navigator.lastItem,
    transitionSpec = transitionSpec,
    modifier = modifier.fillMaxSize(),
    label = "Screen Transform"
  ) { screen ->
    content(screen)

    navigator.replaceAllIfRootScreen(
      isTargetVisible = transition.targetState == Visible,
      isCurrentVisible = transition.currentState == Visible,
      screen = screen
    )
  }
}

private fun hardwareConfirmationContentTransform(
  lastEvent: cafe.adriel.voyager.core.stack.StackEvent,
  fromModel: ScreenModel,
  toModel: ScreenModel,
): ContentTransform? {
  return when {
    lastEvent == Push && toModel.body.usesHardwareConfirmationSlideTransition() ->
      slideOverlayAnimation(AxisAnimationDirection.Forward)
    lastEvent == Pop && fromModel.body.usesHardwareConfirmationSlideTransition() ->
      slideOverlayAnimation(AxisAnimationDirection.Backward)
    else -> null
  }
}

private fun BodyModel.usesHardwareConfirmationSlideTransition(): Boolean {
  return this is CompleteTwoTapBodyModel ||
    this is HardwareConfirmationScreenModel ||
    this is HardwareConfirmationHelpBodyModel ||
    this is HardwareConfirmationCanceledScreenModel ||
    this is HardwareConfirmationResultBodyModel
}

private fun nfcContentTransform(
  lastEvent: cafe.adriel.voyager.core.stack.StackEvent,
  fromModel: ScreenModel,
  toModel: ScreenModel,
): ContentTransform? {
  return when {
    lastEvent == Push && toModel.usesNfcSlideTransition() ->
      slideOverlayAnimation(AxisAnimationDirection.Forward)
    lastEvent == Pop && fromModel.usesNfcSlideTransition() ->
      slideOverlayAnimation(AxisAnimationDirection.Backward)
    else -> null
  }
}

private fun ScreenModel.usesNfcSlideTransition(): Boolean {
  return platformNfcScreen ||
    (
      presentationStyle.isModalPresentationStyle() &&
        (
          body is SignTransactionNfcBodyModel ||
            body is FwupNfcBodyModel
        )
    )
}

private fun shouldSkipTransitionAnimation(
  lastEvent: cafe.adriel.voyager.core.stack.StackEvent,
  fromModel: ScreenModel,
  toModel: ScreenModel,
  isDesignSystemV2Enabled: Boolean,
): Boolean {
  return lastEvent == Replace ||
    lastEvent == Idle ||
    isHomeSecurityHubTransition(fromModel.body, toModel.body) ||
    shouldSkipRealtimeSurfaceTransition(fromModel, toModel, isDesignSystemV2Enabled) ||
    shouldKeepHomeBannerFixed(fromModel, toModel, isDesignSystemV2Enabled)
}

private fun isHomeSecurityHubTransition(
  fromBody: BodyModel,
  toBody: BodyModel,
): Boolean {
  return (fromBody is MoneyHomeBodyModel && toBody is SecurityHubBodyModel) ||
    (fromBody is SecurityHubBodyModel && toBody is MoneyHomeBodyModel)
}

private fun shouldSkipRealtimeSurfaceTransition(
  fromModel: ScreenModel,
  toModel: ScreenModel,
  isDesignSystemV2Enabled: Boolean,
): Boolean {
  if (fromModel.presentationStyle.isModalPresentationStyle() || toModel.presentationStyle.isModalPresentationStyle()) {
    return false
  }

  val fromBody = fromModel.body
  val toBody = toModel.body
  return isDesignSystemV2Enabled &&
    (
      fromBody is DeviceSettingsFormBodyModel ||
        toBody is DeviceSettingsFormBodyModel
    )
}

private fun ScreenPresentationStyle.isModalPresentationStyle(): Boolean {
  return this == Modal || this == ModalFullScreen
}

private fun shouldKeepHomeBannerFixed(
  fromModel: ScreenModel,
  toModel: ScreenModel,
  isDesignSystemV2Enabled: Boolean,
): Boolean {
  return isDesignSystemV2Enabled &&
    isMoneyHomeSettingsTransition(fromModel.body, toModel.body) &&
    (fromModel.statusBannerModel != null || toModel.statusBannerModel != null)
}

private fun isMoneyHomeSettingsTransition(
  fromBody: BodyModel,
  toBody: BodyModel,
): Boolean {
  return (fromBody is MoneyHomeBodyModel && toBody is SettingsBodyModel) ||
    (fromBody is SettingsBodyModel && toBody is MoneyHomeBodyModel)
}

private fun Navigator.replaceAllIfRootScreen(
  isTargetVisible: Boolean,
  isCurrentVisible: Boolean,
  screen: VoyagerScreen,
) {
  if (isTargetVisible && isCurrentVisible && screen.isBackstackRoot()) {
    replaceAll(item = screen)
  }
}

private fun Navigator.popContentTransform(
  previousPresentationStyle: ScreenPresentationStyle,
  currentPresentationStyle: ScreenPresentationStyle,
  density: Density,
): ContentTransform {
  return if (isTransitioningFromSplashScreen()) {
    // Special case for the splash screen: have it fade out
    FadeAnimation
  } else {
    when (previousPresentationStyle) {
      FullScreen ->
        // We're going back from a FullScreen, assume there's only 1 full screen at a time, so
        // animate out of the full screen entirely via Z.Axis (vs an X.Axis Backward).
        sharedAxisAnimation(Axis.Z, AxisAnimationDirection.Backward, density)

      Modal, ModalFullScreen ->
        if (currentPresentationStyle in setOf(Modal, ModalFullScreen)) {
          // We are going from Modal -> Modal, animate on the X.Axis
          sharedAxisAnimation(Axis.X, AxisAnimationDirection.Backward, density)
        } else {
          // We are going from Modal -> not-Modal (Root), animate as a dismiss animation
          slideOverlayAnimation(AxisAnimationDirection.Backward)
        }

      Root, RootFullScreen ->
        // Going backwards from a Root can only be another Root, animate the X.Axis.
        sharedAxisAnimation(Axis.X, AxisAnimationDirection.Backward, density)
    }
  }
}

private fun Navigator.pushContentTransform(
  previousPresentationStyle: ScreenPresentationStyle,
  currentPresentationStyle: ScreenPresentationStyle,
  density: Density,
): ContentTransform {
  return if (isTransitioningFromSplashScreen()) {
    // Special case for the splash screen: have it fade out
    FadeAnimation
  } else {
    when (currentPresentationStyle) {
      FullScreen ->
        // Always animate a new FullScreen on the Z.Axis
        sharedAxisAnimation(Axis.Z, AxisAnimationDirection.Forward, density)

      Modal, ModalFullScreen ->
        when (previousPresentationStyle) {
          Modal, ModalFullScreen ->
            // We're going from Modal -> Modal, animate on the X.Axis
            sharedAxisAnimation(Axis.X, AxisAnimationDirection.Forward, density)

          FullScreen ->
            // We're going from FullScreen -> Modal, animate backwards on the Z.Axis
            sharedAxisAnimation(Axis.Z, AxisAnimationDirection.Backward, density)

          Root, RootFullScreen ->
            // We're going from Root -> Modal, animate upwards as a present animation
            slideOverlayAnimation(AxisAnimationDirection.Forward)
        }

      Root, RootFullScreen ->
        when (previousPresentationStyle) {
          Modal, ModalFullScreen ->
            // We're going from Modal -> Root, animate downwards as a dismiss animation
            slideOverlayAnimation(AxisAnimationDirection.Backward)

          FullScreen ->
            // We're going from FullScreen -> Root, animate backwards on the Z.Axis
            sharedAxisAnimation(Axis.Z, AxisAnimationDirection.Backward, density)

          Root, RootFullScreen -> {
            // We're going from Root -> Root, animate on the X.Axis
            sharedAxisAnimation(Axis.X, AxisAnimationDirection.Forward, density)
          }
        }
    }
  }
}

/**
 * Returns true when this [ScreenModel] is considered the navigation or
 * backstack root screen.  That is, when displayed, all previous screens
 * should no longer be accessible via back navigation.
 */
private fun VoyagerScreen.isBackstackRoot(): Boolean {
  // Always clear the stack on Money Home and Choose Account Access
  val body = (this as UiModelContentScreen).model.body
  val eventTrackerScreenId = body.eventTrackerScreenInfo?.eventTrackerScreenId
  return eventTrackerScreenId == MoneyHomeEventTrackerScreenId.MONEY_HOME ||
    eventTrackerScreenId == GeneralEventTrackerScreenId.CHOOSE_ACCOUNT_ACCESS ||
    body is SplashBodyModel ||
    body is SplashLockModel
}

/**
 * Instead of showing an entirely new screen, in these cases we should
 * just update the model of the current screen
 */
private fun Navigator.shouldReplaceModel(model: ScreenModel): Boolean {
  return isTransitioningFromLoadingToLoading(model) ||
    isTransitioningFromPairHwToPairHw(model) ||
    isTransitioningBetweenNfcScreens(model) ||
    isTransitioningFromFwupToFwup(model) ||
    isTransitioningBetweenSplashBiometricAndSplashLock()
}

private fun Navigator.isTransitioningFromSplashScreen(): Boolean {
  return previousModel()?.body is SplashBodyModel || currentModel().body is SplashBodyModel
}

private fun Navigator.isTransitioningFromLoadingToLoading(newModel: ScreenModel): Boolean {
  return newModel.body is LoadingSuccessBodyModel && currentModel().body is LoadingSuccessBodyModel
}

private fun Navigator.isTransitioningFromPairHwToPairHw(newModel: ScreenModel): Boolean {
  return currentModel().body is PairNewHardwareBodyModel &&
    newModel.body is PairNewHardwareBodyModel
}

private fun Navigator.isTransitioningFromFwupToFwup(newModel: ScreenModel): Boolean {
  return currentModel().body is FwupNfcBodyModel &&
    newModel.body is FwupNfcBodyModel
}

private fun Navigator.isTransitioningBetweenSplashBiometricAndSplashLock(): Boolean {
  return (previousModel()?.body is SplashBodyModel && currentModel().body is SplashLockModel) ||
    (previousModel()?.body is SplashLockModel && currentModel().body is SplashBodyModel)
}

private fun Navigator.isTransitioningBetweenNfcScreens(newModel: ScreenModel): Boolean {
  // NFC progress-state updates should replace the current screen model in place.
  // Recreating the screen resets the Android video background and makes the flow feel jumpy.
  return (currentModel().body is NfcBodyModel && newModel.body is NfcBodyModel) ||
    (
      currentModel().body is SignTransactionNfcBodyModel &&
        newModel.body is SignTransactionNfcBodyModel
    )
}

private fun Navigator.previousModel(): ScreenModel? {
  return items.getOrNull(items.lastIndex - 1)?.let { (it as UiModelContentScreen).model }
}

private fun Navigator.currentModel(): ScreenModel {
  return (lastItem as UiModelContentScreen).model
}

private fun Navigator.currentScreen(): UiModelContentScreen {
  return lastItem as UiModelContentScreen
}
