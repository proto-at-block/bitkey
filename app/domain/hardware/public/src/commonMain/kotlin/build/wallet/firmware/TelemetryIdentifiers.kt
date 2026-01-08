package build.wallet.firmware

data class TelemetryIdentifiers(
  val serial: String,
  val version: String,
  val swType: String,
  val hwRevision: String,
  val mcuInfo: String,
) {
  fun hwRevisionWithoutProduct() =
    // Transform a hwRevision like 'w1a-dvt' to 'dvt'.
    // Memfault prefers the latter.
    hwRevision
      .split("-")
      .last()

  fun hwRevisionWithSwType() =
    // Return something like 'dvt-app-a-dev'. This is required when uploading telemetry
    // events that do not have a build ID included.
    hwRevisionWithoutProduct() + "-" + swType
}
