package build.wallet.statemachine.partnerships.sell

import build.wallet.bitkey.keybox.Keybox
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface PartnershipsSellOptionsUiStateMachine : StateMachine<PartnershipsSellOptionsUiProps, ScreenModel>

data class PartnershipsSellOptionsUiProps(
  val keybox: Keybox,
  val onBack: () -> Unit,
  val onPartnerRedirected: (PartnerRedirectionMethod, PartnershipTransaction) -> Unit,
)
