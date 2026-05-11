package build.wallet.nfc

import bitkey.account.HardwareType
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.nfc.platform.HardwareIdentityAwareNfcCommands
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.W3NfcCommands
import build.wallet.nfc.platform.actualHardwareType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Real-hardware NFC commands proxy that detects W1 vs W3 once per NFC session and routes all
 * subsequent calls to the correct backing implementation.
 *
 * The detected [FirmwareDeviceInfo] is cached only as stable session identity. Later
 * [getDeviceInfo] calls remain live reads so flows like FWUP can observe updated metadata.
 */
internal class ResolvingNfcCommands(
  private val baseCommands: NfcCommands,
  private val commandsProvider: NfcCommandsProvider,
  initialDeviceInfo: FirmwareDeviceInfo? = null,
) : DelegatingW3NfcCommands(), HardwareIdentityAwareNfcCommands {
  private val resolutionMutex = Mutex()
  private var resolution: Resolution? = initialDeviceInfo?.let { createResolution(it) }

  override suspend fun getDeviceInfo(session: NfcSession): FirmwareDeviceInfo {
    val cachedResolution = resolution
    return if (cachedResolution == null) {
      resolve(session).deviceInfo
    } else {
      cachedResolution.commands.getDeviceInfo(session)
    }
  }

  override suspend fun resolvedDeviceInfo(session: NfcSession): FirmwareDeviceInfo =
    resolve(session).deviceInfo

  override suspend fun delegatedCommands(session: NfcSession): NfcCommands =
    resolve(session).commands

  override suspend fun delegatedW3Commands(session: NfcSession): W3NfcCommands {
    val resolvedCommands = delegatedCommands(session)
    return resolvedCommands as? W3NfcCommands
      ?: throw NfcException.WrongHardwareType(
        expected = HardwareType.W3,
        actual = actualHardwareType(session)
      )
  }

  private suspend fun resolve(session: NfcSession): Resolution {
    resolution?.let { return it }

    return resolutionMutex.withLock {
      resolution?.let { return it }

      val deviceInfo = baseCommands.getDeviceInfo(session)
      val resolved = createResolution(deviceInfo)
      resolution = resolved
      resolved
    }
  }

  private fun createResolution(deviceInfo: FirmwareDeviceInfo): Resolution =
    Resolution(
      deviceInfo = deviceInfo,
      commands = commandsProvider.forHardwareType(
        type = deviceInfo.hardwareType(),
        isHardwareFake = false
      )
    )

  private data class Resolution(
    val deviceInfo: FirmwareDeviceInfo,
    val commands: NfcCommands,
  )
}
