package build.wallet.fwup

import build.wallet.compose.collections.immutableListOf
import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import build.wallet.fwup.FwupMode.Delta
import build.wallet.fwup.FwupMode.Normal
import okio.ByteString.Companion.encodeUtf8

val FwupDataMock =
  FwupData(
    version = "fake",
    chunkSize = 0u,
    signatureOffset = 0u,
    appPropertiesOffset = 0u,
    firmware = "firmware".encodeUtf8(),
    signature = "signature".encodeUtf8(),
    fwupMode = Delta
  )

/**
 * Mock MCU firmware data for W1 CORE MCU.
 */
val McuFwupDataMock_W1_CORE =
  McuFwupData(
    mcuRole = McuRole.CORE,
    mcuName = McuName.EFR32,
    version = "1.0.0-fake",
    chunkSize = 452u,
    signatureOffset = 647104u,
    appPropertiesOffset = 1024u,
    firmware = "w1-core-firmware".encodeUtf8(),
    signature = "w1-core-signature".encodeUtf8(),
    fwupMode = Normal
  )

/**
 * Mock MCU firmware data for W3 CORE MCU.
 */
val McuFwupDataMock_W3_CORE =
  McuFwupData(
    mcuRole = McuRole.CORE,
    mcuName = McuName.EFR32,
    version = "2.0.0-fake",
    chunkSize = 452u,
    signatureOffset = 647104u,
    appPropertiesOffset = 1024u,
    firmware = "w3-core-firmware".encodeUtf8(),
    signature = "w3-core-signature".encodeUtf8(),
    fwupMode = Normal
  )

/**
 * Mock MCU firmware data for W3 UXC MCU.
 */
val McuFwupDataMock_W3_UXC =
  McuFwupData(
    mcuRole = McuRole.UXC,
    mcuName = McuName.STM32U5,
    version = "2.0.0-fake",
    chunkSize = 448u,
    signatureOffset = 524288u,
    appPropertiesOffset = 1024u,
    firmware = "w3-uxc-firmware".encodeUtf8(),
    signature = "w3-uxc-signature".encodeUtf8(),
    fwupMode = Normal
  )

/**
 * Mock list of MCU updates for W1 device (single CORE MCU).
 */
val McuFwupDataListMock_W1 = immutableListOf(McuFwupDataMock_W1_CORE)

/**
 * Mock list of MCU updates for W3 device (CORE + UXC).
 * Ordered with CORE first as required by firmware.
 */
val McuFwupDataListMock_W3 = immutableListOf(McuFwupDataMock_W3_CORE, McuFwupDataMock_W3_UXC)
