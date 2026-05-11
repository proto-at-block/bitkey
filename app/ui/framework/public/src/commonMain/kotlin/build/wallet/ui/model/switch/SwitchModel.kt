package build.wallet.ui.model.switch

data class SwitchModel(
  val checked: Boolean,
  val onCheckedChange: (Boolean) -> Unit,
  val enabled: Boolean = true,
  val interactionsEnabled: Boolean = enabled,
  val testTag: String? = null,
)
