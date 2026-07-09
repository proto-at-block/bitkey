package build.wallet.f8e.mobilepay

import bitkey.privilegedactions.PrivilegedActionInstance

/**
 * Returned when sweep signing is blocked until the user completes hardware verification out-of-band.
 */
data class HardwareVerificationRequiredError(
  val privilegedActionInstance: PrivilegedActionInstance,
) : Error("Hardware verification is required before this sweep can continue")
