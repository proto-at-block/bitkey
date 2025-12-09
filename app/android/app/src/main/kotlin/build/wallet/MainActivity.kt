package build.wallet

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATION_OPEN
import build.wallet.di.AndroidActivityComponent
import build.wallet.logging.logInfo
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.ui.app.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
  val appComponent by lazy {
    (application as BitkeyApplication).appComponent
  }

  val activityComponent by lazy {
    CoroutineScope(Dispatchers.Default).async {
      initializeActivityComponent()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    showSplashScreen()

    lifecycleScope.launch {
      // Wait for the app component to be initialized before proceeding
      val appComponent = appComponent.await()
      logAppLaunchState(savedInstanceState, application as BitkeyApplication)

      lockOrientationToPortrait()
      drawContentBehindSystemBars()
      registerLifecycleObservers()

      maybeHideAppInLauncher()

      val activityComponent = activityComponent.await()

      setContent {
        App(
          model = activityComponent.appUiStateMachine.model(Unit),
          deviceInfo = appComponent.deviceInfoProvider.getDeviceInfo(),
          accelerometer = appComponent.accelerometer,
          themePreferenceService = activityComponent.themePreferenceService,
          haptics = appComponent.haptics
        )
      }

      createNotificationChannel()
      logEventIfFromNotification()

      // From a backend notification
      intent?.extras?.let {
        Router.route = Route.from(it.toMap())
      }

      // From a deeplink
      intent?.dataString?.let {
        Router.route = Route.from(it)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    CoroutineScope(Dispatchers.Default).launch {
      activityComponent.await().inAppBrowserNavigator.onClose()
    }
  }

  // Handle deep links when the app is already open
  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    // From a backend notification
    intent?.extras?.let {
      Router.route = Route.from(it.toMap())
    }

    // From a deeplink
    intent?.dataString?.let {
      Router.route = Route.from(it)
    }
  }

  private fun showSplashScreen() {
    // Enable support for Splash Screen API for
    // proper Android 12+ support
    installSplashScreen()
      .also {
        it.setKeepOnScreenCondition { !activityComponent.isCompleted }
      }
  }

  private fun logAppLaunchState(
    savedInstanceState: Bundle?,
    application: BitkeyApplication,
  ) {
    if (application.isFreshLaunch) {
      application.isFreshLaunch = false
      when (savedInstanceState) {
        // When our "isFreshInstance" variable is true, and savedInstance bundle is null, we know it
        // is a completely fresh launch of the app.
        null -> logInfo(tag = "app_lifecycle") { "Fresh Launch" }
        // When our "isFreshInstance" variable is true, and savedInstance bundle is not null, we
        // assume the app is launching from after a process death.
        else -> logInfo(tag = "app_lifecycle") { "Recovering from process death" }
      }
    } else {
      when (savedInstanceState) {
        // When our "isFreshInstance" variable is false, and savedInstance bundle is null, the app is
        // creating a new instance of the activity
        null -> logInfo(tag = "app_lifecycle") { "Creating activity instance" }
        // When our "isFreshInstance" variable is false, and savedInstance bundle is not null, then
        // we know the app is recovering from a configuration change.
        else -> logInfo(tag = "app_lifecycle") { "Recovering from configuration change" }
      }
    }
  }

  private suspend fun createNotificationChannel() {
    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      appComponent.await().notificationChannelRepository.setupChannels()
    }
  }

  private suspend fun logEventIfFromNotification() {
    if (intent.extras?.getBoolean("notification") == true) {
      appComponent.await().eventTracker.track(ACTION_APP_PUSH_NOTIFICATION_OPEN)
    }
  }

  private fun drawContentBehindSystemBars() {
    enableEdgeToEdge()
  }

  @SuppressLint("SourceLockedOrientationActivity")
  private fun lockOrientationToPortrait() {
    requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
  }

  private suspend fun registerLifecycleObservers() {
    lifecycle.apply {
      addObserver(appComponent.await().appLifecycleObserver)
    }
  }

  private suspend fun initializeActivityComponent(): AndroidActivityComponent {
    return appComponent.await().activityComponent(
      fragmentActivity = this,
      lifecycle = lifecycle
    )
  }

  private suspend fun maybeHideAppInLauncher() {
    appComponent.await().biometricPreference.isEnabled()
      .onEach { isEnabled ->
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
          setRecentsScreenshotEnabled(!isEnabled)
        }
      }
      .stateIn(lifecycleScope, SharingStarted.Eagerly, false)
  }
}

fun Bundle.toMap(): Map<String, Any?> {
  val map = mutableMapOf<String, Any?>()
  keySet().forEach { key ->
    map[key] = get(key)
  }
  return map
}
