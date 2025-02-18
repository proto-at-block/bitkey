package build.wallet.statemachine.keypad

import build.wallet.amount.KeypadButton
import build.wallet.ui.model.Model

data class KeypadModel(
  val showDecimal: Boolean,
  val onButtonPress: (KeypadButton) -> Unit,
) : Model
