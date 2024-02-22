package build.wallet.firmware

import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.A
import kotlinx.datetime.Instant
import okio.ByteString

val FirmwareMetadataMock =
  FirmwareMetadata(
    activeSlot = A,
    gitId = "some-fake-id",
    gitBranch = "main",
    version = "1.0",
    build = "mock",
    timestamp = Instant.DISTANT_PAST,
    hash = ByteString.EMPTY,
    hwRevision = "mocky-mcmockface :)"
  )
