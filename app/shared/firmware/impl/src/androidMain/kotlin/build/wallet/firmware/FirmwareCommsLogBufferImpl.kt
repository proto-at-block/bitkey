package build.wallet.firmware

import build.wallet.logging.log
import build.wallet.rust.firmware.disableProtoExchangeLogging
import build.wallet.rust.firmware.enableProtoExchangeLogging
import build.wallet.rust.firmware.getProtoExchangeLogs

class FirmwareCommsLogBufferImpl : FirmwareCommsLogBuffer {
  private var enabled = false

  init {
    disableProtoExchangeLogging()
  }

  override fun configure(enabled: Boolean) {
    if (enabled) {
      log { "FirmwareCommsLogBufferImpl enabled" }
      enableProtoExchangeLogging()
    } else {
      log { "FirmwareCommsLogBufferImpl disabled" }
      disableProtoExchangeLogging()
    }
    this.enabled = enabled
  }

  override fun upload() {
    if (!enabled) {
      // Nothing should be logged in the LogBuffer by the downstream Rust code, but just
      // to be safe, we'll check the feature flag here as well.
      return
    }

    for (l in getProtoExchangeLogs()) {
      log(tag = "WCA") { l }
    }
  }
}
