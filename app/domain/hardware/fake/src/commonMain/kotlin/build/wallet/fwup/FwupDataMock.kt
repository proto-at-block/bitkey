package build.wallet.fwup

import build.wallet.fwup.FwupMode.Delta
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
