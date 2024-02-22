package build.wallet.statemachine.core

import build.wallet.ui.model.Model

data class FloatingActionButtonModel(
  val icon: Icon?,
  val text: String,
  val onClick: () -> Unit,
) : Model()
