package build.wallet

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATION_OPEN
import build.wallet.cloud.store.*
import build.wallet.di.AndroidActivityComponent
import build.wallet.di.AndroidAppComponent
import build.wallet.logging.*
import build.wallet.nfc.*
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.ui.app.App
import build.wallet.ui.app.AppUiModelMap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class MainActivity : FragmentActivity() {
  private lateinit var appComponent: AndroidAppComponent
  private lateinit var activityComponent: AndroidActivityComponent

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Enable support for Splash Screen API for
    // proper Android 12+ support
    installSplashScreen()
    appComponent = (application as BitkeyApplication).appComponent
    logAppLaunchState(savedInstanceState, application as BitkeyApplication)

    lockOrientationToPortrait()
    drawContentBehindSystemBars()

    activityComponent = initializeActivityComponent()
    registerLifecycleObservers()

    maybeHideAppInLauncher()

    setContent {
      App(
        model = activityComponent.appUiStateMachine.model(Unit),
        uiModelMap = AppUiModelMap
      )
    }
    createNotificationChannel()
    logEventIfFromNotification()

    // From a backend notification
    intent?.extras?.getInt(Route.DeepLink.NAVIGATE_TO_SCREEN_ID)?.let {
      Router.route = Route.from(it)
    }
    // From a deeplink
    intent?.dataString?.let {
      Router.route = Route.from(it)
    }
  }

  override fun onResume() {
    super.onResume()
    activityComponent.inAppBrowserNavigator.onClose()
  }

  // Handle deep links when the app is already open
  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    // From a backend notification
    intent?.extras?.getInt(Route.DeepLink.NAVIGATE_TO_SCREEN_ID)?.let {
      Router.route = Route.from(it)
    }
    // From a deeplink
    intent?.dataString?.let {
      Router.route = Route.from(it)
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

  private fun createNotificationChannel() {
    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      appComponent.notificationChannelRepository.setupChannels()
    }
  }

  private fun logEventIfFromNotification() {
    if (intent.extras?.getBoolean("notification") == true) {
      appComponent.eventTracker.track(ACTION_APP_PUSH_NOTIFICATION_OPEN)
    }
  }

  private fun drawContentBehindSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
  }

  @SuppressLint("SourceLockedOrientationActivity")
  private fun lockOrientationToPortrait() {
    requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
  }

  private fun registerLifecycleObservers() {
    lifecycle.apply {
      addObserver(appComponent.appLifecycleObserver)
    }
  }

  private fun initializeActivityComponent(): AndroidActivityComponent {
    return appComponent.activityComponent(
      fragmentActivity = this,
      lifecycle = lifecycle
    )
  }

  private fun maybeHideAppInLauncher() {
    appComponent.biometricPreference.isEnabled()
      .onEach { isEnabled ->
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
          setRecentsScreenshotEnabled(!isEnabled)
        }
      }
      .stateIn(lifecycleScope, SharingStarted.Eagerly, false)
  }
}
