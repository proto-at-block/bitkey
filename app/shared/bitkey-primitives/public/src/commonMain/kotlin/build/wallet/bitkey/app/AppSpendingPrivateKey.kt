package build.wallet.bitkey.app

import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.spending.SpendingPrivateKey

data class AppSpendingPrivateKey(
  override val key: ExtendedPrivateKey,
) : SpendingPrivateKey
