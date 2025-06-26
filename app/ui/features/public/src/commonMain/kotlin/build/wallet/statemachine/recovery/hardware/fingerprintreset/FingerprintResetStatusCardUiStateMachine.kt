package build.wallet.statemachine.recovery.hardware.fingerprintreset

import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel

interface FingerprintResetStatusCardUiStateMachine :
  StateMachine<FingerprintResetStatusCardUiProps, CardModel?>
