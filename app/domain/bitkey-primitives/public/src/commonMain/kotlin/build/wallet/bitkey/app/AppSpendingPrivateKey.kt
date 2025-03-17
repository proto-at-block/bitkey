package build.wallet.bitkey.app

import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.spending.SpendingPrivateKey
import kotlinx.serialization.Serializable

@Serializable(with = AppSpendingPrivateKeySerializer::class)
data class AppSpendingPrivateKey(
  override val key: ExtendedPrivateKey,
) : SpendingPrivateKey
