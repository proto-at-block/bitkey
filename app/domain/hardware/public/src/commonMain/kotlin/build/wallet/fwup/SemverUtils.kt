package build.wallet.fwup

/**
 * Converts a semantic version string (e.g., "2.1.0") to an integer for comparison.
 * String.format only works on Java/Android targets, so this function is a bit complex.
 * Format: MMNNPPP where MM is major (2 digits), NN is minor (2 digits), PPP is patch (3 digits).
 *
 * Examples:
 * - "2.0.0" -> 2000000
 * - "2.1.5" -> 2010005
 * - "10.15.123" -> 10015123
 */
fun semverToInt(semver: String): Int {
  val parts = semver.split('.')

  // Assuming the input is always valid and well-formed
  val major = parts[0].toInt()
  val minor = parts[1].toInt()
  val patch = parts[2].toInt()

  // Validate bounds to ensure proper formatting
  require(major < 100) { "Major version must be less than 100, got: $major" }
  require(minor < 100) { "Minor version must be less than 100, got: $minor" }
  require(patch < 1000) { "Patch version must be less than 1000, got: $patch" }

  // Format the components with leading zeros
  // 2 digits for major, 2 for minor, and 3 for patch.
  val majorFormatted = major.toString().padStart(2, '0')
  val minorFormatted = minor.toString().padStart(2, '0')
  val patchFormatted = patch.toString().padStart(3, '0')

  // Concatenate the components and convert to Int
  val formattedVersion = majorFormatted + minorFormatted + patchFormatted
  return formattedVersion.toInt()
}
