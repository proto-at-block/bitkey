package build.wallet

import android.app.Application
import bitkey.bugsnag.Bugsnag
import bitkey.datadog.AndroidDatadogInitializer
import build.wallet.di.AndroidAppComponent
import build.wallet.di.create
import build.wallet.platform.appVariant
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

@Suppress("unused")
class BitkeyApplication : Application() {
  var isFreshLaunch = true
  lateinit var appComponent: Deferred<AndroidAppComponent>

  override fun onCreate() {
    super.onCreate()
    // Initialize crash reporters as soon as possible.
    AndroidDatadogInitializer(
      context = this,
      appVariant = appVariant
    ).initialize()
    Bugsnag.initialize(
      application = this,
      appVariant = appVariant
    )

    appComponent = CoroutineScope(Dispatchers.Default).async {
      // Initialize the app component in a background thread to avoid blocking the main thread and
      // potentially causing ANRs
      AndroidAppComponent::class.create(
        application = this@BitkeyApplication,
        appId = AppId(BuildConfig.APPLICATION_ID),
        appVariant = appVariant,
        appVersion = AppVersion(BuildConfig.VERSION_NAME)
      ).also {
        it.loggingInitializer.initialize()
        it.bugsnagContext.configureCommonMetadata()
        it.strictModeEnabler.configure()
      }
    }
  }
}
