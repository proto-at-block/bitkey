package build.wallet.statemachine.core.input

import build.wallet.ui.model.input.TextFieldModel

fun TextFieldModel.onValueChange(text: String) = onValueChange(text, 0..0)
