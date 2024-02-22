package build.wallet.fwup

sealed class ParseFwupManifestError : Error() {
  /**
   * There was an error when trying to deserialize the JSON firmware bundle
   */
  data class ParsingError(override val cause: Throwable) : ParseFwupManifestError()

  /**
   * The manifest version of the firmware is incompatible with the current
   * expected manifest version
   */
  data object UnknownManifestVersion : ParseFwupManifestError()

  /**
   * The firmware version to udpate to is <= the current firmware version.
   */
  object NoUpdateNeeded : ParseFwupManifestError()
}
