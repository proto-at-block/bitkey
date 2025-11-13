package build.wallet.bitkey.f8e

import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.Serializable

/**
 * Represents f8e (server) spending keyset.
 *
 * ⚠️⚠️⚠️ ---- DO NOT ADD DEFAULT VALUES ----- ⚠️⚠️⚠️
 *
 * See F8eSpendingKeysetColumnAdapter.kt for more
 *
 * @property keysetId remote id of the keyset.
 * @property spendingPublicKey spending f8e dpub.
 * @property privateWalletRootXpub server root xpub - only present for private wallets, used to calculate psbt tweaks
 */
@Serializable
data class F8eSpendingKeyset(
  val keysetId: String,
  val spendingPublicKey: F8eSpendingPublicKey,
  @Redacted
  val privateWalletRootXpub: String?,
)

val F8eSpendingKeyset.isPrivateWallet: Boolean
  get() = privateWalletRootXpub != null
