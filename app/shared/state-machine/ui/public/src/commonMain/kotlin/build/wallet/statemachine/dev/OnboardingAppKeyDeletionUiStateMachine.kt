package build.wallet.statemachine.dev

import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel

/**
 * State machine for deleting the persisted app key during onboarding in order to generate a new one.
 */
interface OnboardingAppKeyDeletionUiStateMachine : StateMachine<Unit, ListGroupModel?>
