package build.wallet.emergencyexitkit

/**
 * Describes whether the app build is associated with an Emergency Exit Kit (EEK).
 */
sealed interface EmergencyExitKitAssociation {
  /**
   * This APK was not built with Emergency Exit Kit information specified.
   */
  data object Unavailable : EmergencyExitKitAssociation

  /**
   * This build is an EEK build, therefore has no associated EEK data.
   */
  data object EekBuild : EmergencyExitKitAssociation

  /**
   * Build is associated with the hash of an Emergency Exit Kit.
   */
  data class AssociatedData(
    /**
     * The expected hash of the Emergency Exit Kit APK File.
     */
    val hash: String,
    /**
     * A public URL where the Emergency Exit Kit APK can be downloaded.
     */
    val url: String,
    /**
     * The version number of the Emergency Exit Kit APK.
     */
    val version: String,
  ) : EmergencyExitKitAssociation
}
