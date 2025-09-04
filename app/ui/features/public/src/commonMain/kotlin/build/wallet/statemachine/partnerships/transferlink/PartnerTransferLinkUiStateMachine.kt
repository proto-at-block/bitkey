package build.wallet.statemachine.partnerships.transferlink

import build.wallet.partnerships.PartnerTransferLinkRequest
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * UI State Machine for managing the partner transfer link flow.
 *
 * This state machine handles the end-to-end process of creating and processing transfer links
 * with partnership platforms (e.g., Strike). It manages user confirmation, link creation,
 * and redirection to the partner platform via deeplinks or web browsers.
 */
interface PartnerTransferLinkUiStateMachine : StateMachine<PartnerTransferLinkProps, ScreenModel>

data class PartnerTransferLinkProps(
  val hostScreen: ScreenModel,
  val request: PartnerTransferLinkRequest,
  val onComplete: () -> Unit,
  val onExit: () -> Unit,
)
