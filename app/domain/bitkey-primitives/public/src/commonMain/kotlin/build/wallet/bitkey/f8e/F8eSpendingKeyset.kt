package build.wallet.bitkey.f8e

/**
 * Represents f8e (server) spending keyset.
 *
 * @property keysetId remote id of the keyset.
 * @property spendingPublicKey spending f8e dpub.
 */
data class F8eSpendingKeyset(
  val keysetId: String,
  val spendingPublicKey: F8eSpendingPublicKey,
)
