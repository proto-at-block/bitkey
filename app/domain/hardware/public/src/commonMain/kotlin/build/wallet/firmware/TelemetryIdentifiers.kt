package build.wallet.firmware

data class TelemetryIdentifiers(
  val serial: String,
  val version: String,
  val swType: String,
  val hwRevision: String,
  val mcuInfo: String,
) {
  private fun isW3() = hwRevision.startsWith("w3", ignoreCase = true)

  fun memfaultHwRevision() =
    if (isW3()) {
      // W3: 'w3a-core-evt' stays as 'w3a-core-evt'.
      // The full hardware revision string is needed to distinguish
      // W3 devices from W1 in Memfault.
      hwRevision
    } else {
      // W1: 'w1a-dvt' → 'dvt'.
      hwRevision.split("-").last()
    }

  fun hwRevisionWithSwType() =
    // Return something like 'dvt-app-a-dev' (W1) or 'w3a-core-evt-app-a-dev' (W3).
    // This is required when uploading telemetry events that do not have a build ID included.
    memfaultHwRevision() + "-" + swType
}
