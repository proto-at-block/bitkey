package build.wallet.statemachine.data.lightning

import build.wallet.statemachine.core.StateMachine

interface LightningNodeDataStateMachine : StateMachine<Unit, LightningNodeData>
