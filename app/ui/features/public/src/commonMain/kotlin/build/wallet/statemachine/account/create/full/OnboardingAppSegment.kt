package build.wallet.statemachine.account.create.full

import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.childSegment

/**
 * App segments representing onboarding flows.
 */
internal object OnboardingAppSegment : AppSegment {
  override val id: String = "Onboarding"

  object FullAccount : AppSegment by OnboardingAppSegment.childSegment("FullAccount")

  object LiteToFullAccountUpgrade : AppSegment by OnboardingAppSegment.childSegment("LiteToFullAccountUpgrade")
}
