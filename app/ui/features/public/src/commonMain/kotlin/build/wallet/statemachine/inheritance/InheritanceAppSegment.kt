package build.wallet.statemachine.inheritance

import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.childSegment

/**
 * App segments used in the inheritance flows.
 */
object InheritanceAppSegment : AppSegment {
  override val id: String = "Inheritance"

  /**
   * Operations related to the benefactor's attempt to set up an inheritance.
   */
  object Benefactor : AppSegment by childSegment("Benefactor") {
    /**
     * Benefactor invite was accepted opening an activation page
     */
    object Invite : AppSegment by childSegment("Invite")
  }

  /**
   * Operations related to the beneficiary's attempt to claim an inheritance.
   */
  object BeneficiaryClaim : AppSegment by childSegment("Claim") {
    /**
     * Beneficiary process to start an inheritance claim
     */
    object Start : AppSegment by childSegment("Start")

    /**
     * Beneficiary process to cancel an inheritance claim
     */
    object Cancel : AppSegment by childSegment("Cancel")

    /**
     * Beneficiary process to complete an inheritance claim
     */
    object Complete : AppSegment by childSegment("Complete")
  }

  /**
   * Operations related to the benefactor's actions on an inheritance claim.
   */
  object BenefactorClaim : AppSegment by childSegment("Claim") {
    /**
     * Benefactor process to deny (cancel) an inheritance claim
     */
    object Deny : AppSegment by childSegment("Deny")
  }
}
