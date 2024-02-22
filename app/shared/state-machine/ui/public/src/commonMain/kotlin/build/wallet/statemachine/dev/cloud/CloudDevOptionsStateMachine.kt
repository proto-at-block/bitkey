package build.wallet.statemachine.dev.cloud

import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

/**
 * Provides a lower level development options for managing cloud account and storage access.
 */
interface CloudDevOptionsStateMachine : StateMachine<CloudDevOptionsProps, BodyModel>

data class CloudDevOptionsProps(
  val onExit: () -> Unit,
)
