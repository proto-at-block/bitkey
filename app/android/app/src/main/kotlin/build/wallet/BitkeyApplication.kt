package build.wallet

import android.app.Application
import build.wallet.bugsnag.Bugsnag
import build.wallet.datadog.AndroidDatadogInitializer
import build.wallet.di.AndroidAppComponent
import build.wallet.di.create
import build.wallet.platform.appVariant
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVersion

@Suppress("unused")
class BitkeyApplication : Application() {
  var isFreshLaunch = true
  lateinit var appComponent: AndroidAppComponent

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

    appComponent = AndroidAppComponent::class.create(
      application = this,
      appId = AppId(BuildConfig.APPLICATION_ID),
      appVariant = appVariant,
      appVersion = AppVersion(BuildConfig.VERSION_NAME)
    )
    appComponent.loggingInitializer.initialize()
    appComponent.bugsnagContext.configureCommonMetadata()
    appComponent.strictModeEnabler.configure()
  }
}
