package build.wallet.bitkey.inheritance

import kotlinx.serialization.Serializable

/**
 * Inheritance data uploaded by a customer.
 *
 * This data is encrypted per-beneficiary as a package of data that allows
 * the beneficiary to perform an inheritance transaction.
 */
@Serializable
data class InheritanceMaterial(
  val packages: List<InheritanceMaterialPackage>,
)
