package build.wallet.emergencyaccesskit

/**
 * Provides access to build information for Emergency Exit Kit (EEK) builds.
 */
interface EmergencyAccessKitDataProvider {
  /**
   * Determine which, if any, EEK this build is associated with.
   */
  fun getAssociatedEakData(): EmergencyAccessKitAssociation
}
