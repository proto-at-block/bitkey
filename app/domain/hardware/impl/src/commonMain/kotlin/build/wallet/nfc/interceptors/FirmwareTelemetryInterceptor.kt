package build.wallet.nfc.interceptors

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.feature.isEnabled
import build.wallet.firmware.*
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.toByteString
import okio.ByteString

/**
 * Collects firmware telemetry and (1) persists it locally and (2) uploads it to Memfault.
 */
internal fun collectFirmwareTelemetry(
  firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  firmwareTelemetryUploader: FirmwareTelemetryUploader,
  firmwareCommsLogBuffer: FirmwareCommsLogBuffer,
  firmwareCommsLoggingFeatureFlag: FeatureFlag<BooleanFlag>,
) = NfcTransactionInterceptor { next ->
  val interceptor = FirmwareTelemetryInterceptor(firmwareDeviceInfoDao, firmwareTelemetryUploader)

  firmwareCommsLogBuffer.configure(firmwareCommsLoggingFeatureFlag.isEnabled())

  (
    { session, commands ->
      next(session, commands)

      // TODO(W-8000) The query auth causes breakage on android; we see TagLost and then
      // it looks like the FWUP failed but it didn't.

      // Only attempt to collect telemetry if the hardware is unlocked, because
      // the telemetry endpoints require authentication itself.
      if (!session.parameters.skipFirmwareTelemetry && commands.queryAuthentication(session)) {
        val deviceInfo = commands.getDeviceInfo(session)
        interceptor.persistDeviceInfo(deviceInfo)
        interceptor.uploadTelemetry(deviceInfo, commands, session)
      }

      firmwareCommsLogBuffer.upload()
    }
  )
}

private class FirmwareTelemetryInterceptor(
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val firmwareTelemetryUploader: FirmwareTelemetryUploader,
) {
  suspend fun persistDeviceInfo(deviceInfo: FirmwareDeviceInfo) {
    firmwareDeviceInfoDao
      .setDeviceInfo(deviceInfo)
      .logFailure { "Error persisting FirmwareDeviceInfo" }
  }

  suspend fun uploadTelemetry(
    deviceInfo: FirmwareDeviceInfo,
    commands: NfcCommands,
    session: NfcSession,
  ) {
    deviceInfo.mcuInfo.forEach { info ->
      val identifiers =
        TelemetryIdentifiers(
          serial = deviceInfo.serial,
          version = deviceInfo.version,
          swType = deviceInfo.swType,
          hwRevision = deviceInfo.hwRevision,
          mcuInfo = "${info.mcuRole.name}:${info.mcuName.name}:${info.firmwareVersion}"
        )

      getEvents(commands, session, info.mcuRole)?.let {
        firmwareTelemetryUploader.addEvents(it, identifiers)
      }

      readFirmwareTelemetryCoredumpForMcu(commands, session, info.mcuRole)?.let {
        firmwareTelemetryUploader.addCoredump(it, identifiers)
      }
    }
  }

  private suspend fun getEvents(
    commands: NfcCommands,
    session: NfcSession,
    mcuRole: McuRole,
  ) = mutableListOf<UByte>()
    .apply {
      while (true) {
        val events = commands.getEvents(session, mcuRole)
        addAll(events.fragment)
        if (events.remainingSize == 0) break
      }
    }.toByteString()
    .let { if (it.size == 0) null else it }
}

internal suspend fun readFirmwareTelemetryCoredumpForMcu(
  commands: NfcCommands,
  session: NfcSession,
  mcuRole: McuRole,
): ByteString? {
  if (commands.getCoredumpCount(session) == 0) return null

  return mutableListOf<UByte>()
    .apply {
      var offset = 0
      while (true) {
        val currentOffset = offset
        val fragment = commands.getCoredumpFragment(session, offset, mcuRole)
        addAll(fragment.data)
        offset = fragment.offset

        val didOffsetAdvance = offset != currentOffset
        if (!didOffsetAdvance && !fragment.complete) {
          logWarn {
            "Coredump fragment did not advance offset (offset=$offset, complete=${fragment.complete})"
          }
        }
        if (fragment.complete || !didOffsetAdvance) break
      }
    }
    .toByteString()
}
