package build.wallet.firmware

/** Maps to [FirmwareFeatureFlag] in core */
enum class FirmwareFeatureFlag {
  Telemetry,
  DeviceInfoFlag,
  RateLimitTemplateUpdate,
  Unlock,
}

/** Maps to [FirmwareFeatureFlagCfg] in core */
data class FirmwareFeatureFlagCfg(
  val flag: FirmwareFeatureFlag,
  val enabled: Boolean,
)
