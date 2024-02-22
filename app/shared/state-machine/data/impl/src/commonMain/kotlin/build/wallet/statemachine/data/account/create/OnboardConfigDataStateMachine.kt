package build.wallet.statemachine.data.account.create

import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.OnboardConfigData

/**
 * Data state machine for reading and updating [OnboardConfig].
 */
interface OnboardConfigDataStateMachine : StateMachine<Unit, OnboardConfigData>
