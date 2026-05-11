package build.wallet.firmware

data class TelemetryIdentifiers(
  val serial: String,
  val version: String,
  val swType: String,
  val hwRevision: String,
  val mcuInfo: String,
  val mcuRole: McuRole? = null,
  val activeSlot: FirmwareMetadata.FirmwareSlot? = null,
) {
  private fun isW3() = hwRevision.startsWith("w3", ignoreCase = true)

  fun memfaultHwRevision() =
    if (isW3()) {
      // W3: 'w3a-core-evt' stays scoped to the MCU reporting telemetry.
      // The full hardware revision string is needed to distinguish
      // W3 devices from W1 in Memfault.
      w3McuScopedHwRevision()
    } else {
      // W1: 'w1a-dvt' → 'dvt'.
      hwRevision.split("-").last()
    }

  private fun w3McuScopedHwRevision(): String {
    val role = mcuRole ?: return hwRevision
    val parts = hwRevision.split("-")
    if (parts.size < 3) return hwRevision

    val mcu = when (role) {
      McuRole.CORE -> "core"
      McuRole.UXC -> "uxc"
    }

    return listOf(parts.first(), mcu, parts.drop(2).joinToString("-")).joinToString("-")
  }

  fun memfaultSoftwareType(): String {
    val slot = activeSlot ?: return swType
    val buildEnv = Regex("""^app-[ab]-(.+)$""")
      .matchEntire(swType)
      ?.groupValues
      ?.get(1)
      ?: return swType

    val slotName = when (slot) {
      FirmwareMetadata.FirmwareSlot.A -> "a"
      FirmwareMetadata.FirmwareSlot.B -> "b"
    }

    return "app-$slotName-$buildEnv"
  }

  fun hwRevisionWithSwType() =
    // Return something like 'dvt-app-a-dev' (W1) or 'w3a-core-evt-app-a-dev' (W3).
    // This is required when uploading telemetry events that do not have a build ID included.
    memfaultHwRevision() + "-" + memfaultSoftwareType()
}
