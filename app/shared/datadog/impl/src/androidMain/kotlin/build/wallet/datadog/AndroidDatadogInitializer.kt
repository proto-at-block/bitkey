package build.wallet.datadog

import android.content.Context
import android.util.Log
import build.wallet.platform.config.AppVariant
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.privacy.TrackingConsent.GRANTED
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import io.opentracing.util.GlobalTracer

// TODO: explain why not using DI here
class AndroidDatadogInitializer(
  private val context: Context,
  private val appVariant: AppVariant,
) : DatadogInitializer {
  private val config = DatadogConfig.create(appVariant)

  override fun initialize() {
    if (Datadog.isInitialized()) return

    val configuration =
      Configuration.Builder(
        clientToken = DATADOG_CLIENT_TOKEN,
        env = config.environmentName
      )
        .setFirstPartyHosts(config.firstPartyHosts)
        .setCrashReportsEnabled(crashReportsEnabled = true)
        .useSite(DatadogSite.valueOf(config.siteName))
        .build()

    Datadog.initialize(context, configuration, GRANTED)

    // Log Datadog SDK errors in development builds to catch SDK configuration errors.
    if (appVariant == AppVariant.Development) {
      Datadog.setVerbosity(Log.WARN)
    }

    Rum.enable(
      RumConfiguration.Builder(applicationId = DATADOG_RUM_APP_ID)
        .trackUserInteractions()
        .trackLongTasks(longTaskThresholdMs = 200)
        .trackBackgroundEvents(enabled = true)
        .setTelemetrySampleRate(sampleRate = 100f)
        .setSessionSampleRate(sampleRate = 100f)
        .build()
    )

    Trace.enable(
      TraceConfiguration.Builder()
        .setNetworkInfoEnabled(enabled = true)
        .build()
    )

    GlobalTracer.registerIfAbsent(
      AndroidTracer.Builder()
        .setBundleWithRumEnabled(enabled = true)
        .setSampleRate(sampleRate = 100.0)
        .build()
    )

    Logs.enable(LogsConfiguration.Builder().build())
  }

  // TODO(W-1144) externalize configs
  private companion object {
    const val DATADOG_CLIENT_TOKEN = "pub815ac5aa736ac399c62cba1627df1ef5"
    const val DATADOG_RUM_APP_ID = "322cdda2-16b3-465b-b25c-a310b8a65a68"
  }
}
