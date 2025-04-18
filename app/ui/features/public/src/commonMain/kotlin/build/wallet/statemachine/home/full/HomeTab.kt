package build.wallet.statemachine.home.full

import build.wallet.statemachine.core.Icon

sealed class HomeTab(
  open val selected: Boolean,
  open val onSelected: () -> Unit,
  val icon: Icon,
  open val badged: Boolean = false,
) {
  data class MoneyHome(
    override val selected: Boolean,
    override val onSelected: () -> Unit,
  ) : HomeTab(selected, onSelected, if (selected) Icon.SmallIconWalletFilled else Icon.SmallIconWallet)

  data class SecurityHub(
    override val selected: Boolean,
    override val onSelected: () -> Unit,
    override val badged: Boolean,
  ) : HomeTab(
      selected,
      onSelected,
      if (selected) Icon.SmallIconShieldFilled else Icon.SmallIconShield
    )
}
