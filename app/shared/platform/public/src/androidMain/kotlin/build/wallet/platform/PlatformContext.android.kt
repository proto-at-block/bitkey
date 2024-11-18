package build.wallet.platform

import android.app.Application
import android.content.Context

/**
 * @property appContext Android [Application]'s [Context].
 */
actual class PlatformContext(application: Application) {
  val appContext: Context = application
}
