package build.wallet.statemachine.core

/**
 * A simplified model of what constitutes a button
 * to be used in screen models
 */
data class ButtonDataModel(
  val text: String,
  val isLoading: Boolean = false,
  val onClick: () -> Unit,
  val leadingIcon: Icon? = null,
)
