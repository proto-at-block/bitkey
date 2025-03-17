package build.wallet.debug

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Development
import java.util.concurrent.Executors

@BitkeyInject(AppScope::class)
class StrictModeEnablerImpl(
  private val appVariant: AppVariant,
) : StrictModeEnabler {
  override fun configure() {
    if (enabled()) {
      StrictMode.setThreadPolicy(
        ThreadPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .reportErrorOnViolation()
          .build()
      )
      StrictMode.setVmPolicy(
        VmPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .reportErrorOnViolation()
          .build()
      )
      logDebug { "Android Strict mode is enabled." }
    }
  }

  private fun enabled(): Boolean = appVariant == Development
}

/**
 * Logs and reports a [VmPolicy] violation. Only available on Android P and above.
 */
private fun VmPolicy.Builder.reportErrorOnViolation(): VmPolicy.Builder {
  return if (VERSION.SDK_INT >= VERSION_CODES.P) {
    penaltyListener(Executors.newSingleThreadExecutor()) {
      logStrictModeViolation(it)
    }
  } else {
    this
  }
}

/**
 * Logs and reports a [ThreadPolicy] violation. Only available on Android P and above.
 */
private fun ThreadPolicy.Builder.reportErrorOnViolation(): ThreadPolicy.Builder {
  return if (VERSION.SDK_INT >= VERSION_CODES.P) {
    penaltyListener(Executors.newSingleThreadExecutor()) {
      logStrictModeViolation(it)
    }
  } else {
    this
  }
}

private fun logStrictModeViolation(throwable: Throwable) {
  logWarn(
    tag = STRICT_MODE_TAG,
    throwable = throwable
  ) {
    "Strict mode violation: $throwable"
  }
}

private const val STRICT_MODE_TAG = "StrictMode"
