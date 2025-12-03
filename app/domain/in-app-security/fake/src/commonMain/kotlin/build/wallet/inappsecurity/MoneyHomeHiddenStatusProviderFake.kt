package build.wallet.inappsecurity

import kotlinx.coroutines.flow.MutableStateFlow

class MoneyHomeHiddenStatusProviderFake : MoneyHomeHiddenStatusProvider {
  override val hiddenStatus = MutableStateFlow(
    MoneyHomeHiddenStatus.VISIBLE
  )

  override fun toggleStatus() {
    hiddenStatus.value = when (hiddenStatus.value) {
      MoneyHomeHiddenStatus.VISIBLE -> MoneyHomeHiddenStatus.HIDDEN
      MoneyHomeHiddenStatus.HIDDEN -> MoneyHomeHiddenStatus.VISIBLE
    }
  }
}
