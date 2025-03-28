package build.wallet.firmware

enum class FingerprintEnrollmentStatus {
  UNSPECIFIED,
  INCOMPLETE,
  COMPLETE,
  NOT_IN_PROGRESS,
}

data class FingerprintEnrollmentDiagnostics(
  val fingerCoverageValid: Boolean,
  val fingerCoverage: Int,
  val commonModeNoiseValid: Boolean,
  val commonModeNoise: Int,
  val imageQualityValid: Boolean,
  val imageQuality: Int,
  val sensorCoverageValid: Boolean,
  val sensorCoverage: Int,
  val templateDataUpdateValid: Boolean,
  val templateDataUpdate: Int,
)

data class FingerprintEnrollmentResult(
  var status: FingerprintEnrollmentStatus,
  val passCount: UInt?,
  val failCount: UInt?,
  val diagnostics: FingerprintEnrollmentDiagnostics?,
)
