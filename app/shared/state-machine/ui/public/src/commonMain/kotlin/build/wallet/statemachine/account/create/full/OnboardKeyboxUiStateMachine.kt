package build.wallet.statemachine.account.create.full

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull

/**
 * A state machine for onboarding a brand new keybox.
 */
interface OnboardKeyboxUiStateMachine : StateMachine<OnboardKeyboxUiProps, ScreenModel>

/**
 * [onboardKeyboxData] - Data for managing the state of onboarding.
 */
data class OnboardKeyboxUiProps(
  val onboardKeyboxData: OnboardKeyboxDataFull,
)
