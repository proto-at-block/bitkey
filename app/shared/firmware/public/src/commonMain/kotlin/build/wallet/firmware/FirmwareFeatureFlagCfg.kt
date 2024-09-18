package build.wallet.firmware

/** Maps to [FirmwareFeatureFlag] in core */
enum class FirmwareFeatureFlag {
  TELEMETRY,
  DEVICE_INFO_FLAG,
  RATE_LIMIT_TEMPLATE_UPDATE,
  UNLOCK,
  MULTIPLE_FINGERPRINTS,
  IMPROVED_FINGERPRINT_ENROLLMENT,
  ASYNC_SIGNING,
}

/** Maps to [FirmwareFeatureFlagCfg] in core */
data class FirmwareFeatureFlagCfg(
  val flag: FirmwareFeatureFlag,
  val enabled: Boolean,
)
