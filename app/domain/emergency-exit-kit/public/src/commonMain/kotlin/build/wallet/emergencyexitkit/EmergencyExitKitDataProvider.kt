package build.wallet.emergencyexitkit

/**
 * Provides access to build information for Emergency Exit Kit (EEK) builds.
 */
interface EmergencyExitKitDataProvider {
  /**
   * Determine which, if any, EEK this build is associated with.
   */
  fun getAssociatedEekData(): EmergencyExitKitAssociation
}
