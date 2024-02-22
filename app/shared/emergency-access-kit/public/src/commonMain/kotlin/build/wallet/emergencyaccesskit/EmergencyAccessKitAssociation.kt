package build.wallet.emergencyaccesskit

/**
 * Describes whether the app build is associated with an Emergency Access Kit (EAK).
 */
sealed interface EmergencyAccessKitAssociation {
  /**
   * This APK was not built with Emergency Access Kit information specified.
   */
  data object Unavailable : EmergencyAccessKitAssociation

  /**
   * This build is an EAK build, therefore has no associated EAK data.
   */
  data object EakBuild : EmergencyAccessKitAssociation

  /**
   * Build is associated with the hash of an Emergency Access Kit.
   */
  data class AssociatedData(
    /**
     * The expected hash of the Emergency Access Kit APK File.
     */
    val hash: String,
    /**
     * A public URL where the Emergency Access Kit APK can be downloaded.
     */
    val url: String,
    /**
     * The version number of the Emergency Access Kit APK.
     */
    val version: String,
  ) : EmergencyAccessKitAssociation
}
