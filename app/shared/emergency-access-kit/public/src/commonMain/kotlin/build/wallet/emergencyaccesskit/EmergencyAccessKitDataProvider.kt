package build.wallet.emergencyaccesskit

/**
 * Provides access to build information for Emergency Access Kit (EAK) builds.
 */
interface EmergencyAccessKitDataProvider {
  /**
   * Determine which, if any, Eak this build is associated with.
   */
  fun getAssociatedEakData(): EmergencyAccessKitAssociation
}
