package build.wallet.statemachine.inheritance

import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.childSegment

/**
 * App segments used in the inheritance flows.
 */
object InheritanceAppSegment : AppSegment {
  override val id: String = "Inheritance"

  /**
   * Operations related to the beneficiary's attempt to claim an inheritance.
   */
  object BeneficiaryClaim : AppSegment by childSegment("Claim") {
    /**
     * Beneficiary process to start an inheritance claim
     */
    object Start : AppSegment by childSegment("Start")
  }
}
