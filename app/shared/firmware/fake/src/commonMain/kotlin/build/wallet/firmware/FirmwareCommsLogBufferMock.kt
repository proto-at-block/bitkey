package build.wallet.firmware

class FirmwareCommsLogBufferMock : FirmwareCommsLogBuffer {
  override fun configure(enabled: Boolean) {
    // no-op
  }

  override fun upload() {
    // no-op
  }
}
