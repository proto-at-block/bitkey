package build.wallet.statemachine.home.full

sealed class HomeTab(
  open val selected: Boolean,
  open val onSelected: () -> Unit,
) {
  data class MoneyHome(
    override val selected: Boolean,
    override val onSelected: () -> Unit,
  ) : HomeTab(selected, onSelected)

  data class SecurityHub(
    override val selected: Boolean,
    override val onSelected: () -> Unit,
  ) : HomeTab(selected, onSelected)
}
