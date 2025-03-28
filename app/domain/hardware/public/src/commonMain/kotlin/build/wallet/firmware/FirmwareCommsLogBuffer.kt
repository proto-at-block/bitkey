package build.wallet.firmware

interface FirmwareCommsLogBuffer {
  fun configure(enabled: Boolean)

  fun upload()
}
