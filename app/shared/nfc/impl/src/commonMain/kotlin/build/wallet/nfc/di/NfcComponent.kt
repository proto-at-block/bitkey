package build.wallet.nfc.di

import build.wallet.analytics.events.EventTracker
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.datadog.DatadogTracer
import build.wallet.di.ActivityScope
import build.wallet.di.SingleIn
import build.wallet.feature.flags.FirmwareCommsLoggingFeatureFlag
import build.wallet.firmware.FirmwareCommsLogBuffer
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.firmware.FirmwareTelemetryUploader
import build.wallet.nfc.NfcCommandsProvider
import build.wallet.nfc.NfcTransactor
import build.wallet.nfc.NfcTransactorImpl
import build.wallet.nfc.haptics.NfcHaptics
import build.wallet.nfc.interceptors.*
import build.wallet.nfc.platform.NfcSessionProvider
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(ActivityScope::class)
interface NfcComponent {
  @Provides
  @SingleIn(ActivityScope::class) // NfcTransactorImpl is stateful.
  fun provideNfcTransactor(
    nfcCommandsProvider: NfcCommandsProvider,
    nfcSessionProvider: NfcSessionProvider,
    firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
    firmwareTelemetryUploader: FirmwareTelemetryUploader,
    firmwareCommsLogBuffer: FirmwareCommsLogBuffer,
    firmwareCommsLoggingFeatureFlag: FirmwareCommsLoggingFeatureFlag,
    nfcHaptics: NfcHaptics,
    datadogRumMonitor: DatadogRumMonitor,
    datadogTracer: DatadogTracer,
    eventTracker: EventTracker,
  ): NfcTransactor {
    return NfcTransactorImpl(
      commandsProvider = nfcCommandsProvider,
      sessionProvider = nfcSessionProvider,
      interceptors =
        listOf(
          retryCommands(),
          iosMessages(),
          collectFirmwareTelemetry(
            firmwareDeviceInfoDao = firmwareDeviceInfoDao,
            firmwareTelemetryUploader = firmwareTelemetryUploader,
            firmwareCommsLogBuffer = firmwareCommsLogBuffer,
            firmwareCommsLoggingFeatureFlag = firmwareCommsLoggingFeatureFlag
          ),
          lockDevice(),
          haptics(nfcHaptics),
          timeoutSession(),
          collectMetrics(
            datadogRumMonitor = datadogRumMonitor,
            datadogTracer = datadogTracer,
            eventTracker = eventTracker
          ),
          sessionLogger()
        )
    )
  }
}
