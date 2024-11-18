package build.wallet.statemachine.dev

import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel

/**
 * State machine for dev options to skip onboarding steps.
 *
 * Emits `null` when using [AppVariant.Customer].
 */
interface OnboardingConfigStateMachine : StateMachine<Unit, ListGroupModel?>
