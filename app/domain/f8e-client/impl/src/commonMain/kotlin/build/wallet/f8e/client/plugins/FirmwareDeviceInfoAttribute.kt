package build.wallet.f8e.client.plugins

import build.wallet.firmware.FirmwareDeviceInfo
import io.ktor.util.AttributeKey

internal val FirmwareDeviceInfoAttribute =
  AttributeKey<FirmwareDeviceInfo>("firmware-device-info")
