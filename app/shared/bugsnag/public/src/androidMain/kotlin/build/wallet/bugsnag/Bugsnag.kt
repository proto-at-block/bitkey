package build.wallet.bugsnag

import android.app.Application
import build.wallet.platform.config.AppVariant
import co.touchlab.crashkios.bugsnag.enableBugsnag
import com.bugsnag.android.BreadcrumbType
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration

object Bugsnag {
  /**
   * Android Bugsnag initializer. This should be called by Android [Application] as soon as possible
   * to start catching crashes.
   */
  fun initialize(
    appVariant: AppVariant,
    application: Application,
  ) {
    // Initialize Bugsnag using Android SDK.
    val bugsnagConfig = application.bugsnagConfiguration(appVariant)
    Bugsnag.start(application, bugsnagConfig)

    // Pipe Bugsnag calls through KMP wrapper.
    // Allows us to configure Bugsnag (e.g. add metadata) from KMP code.
    enableBugsnag()
  }

  private fun Application.bugsnagConfiguration(appVariant: AppVariant): Configuration {
    // Get common config
    val config = BugsnagConfig(appVariant)
    // Bugsnag API key is loaded from app's AndroidManifest.xml
    return Configuration.load(this).apply {
      releaseStage = config.releaseStage
      enabledBreadcrumbTypes = BreadcrumbType.entries.toMutableSet()
    }
  }
}
