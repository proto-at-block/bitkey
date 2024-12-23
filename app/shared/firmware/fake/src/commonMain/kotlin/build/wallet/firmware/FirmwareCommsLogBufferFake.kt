package build.wallet.firmware

class FirmwareCommsLogBufferFake : FirmwareCommsLogBuffer {
  override fun configure(enabled: Boolean) {
    // no-op
  }

  override fun upload() {
    // no-op
  }
}
