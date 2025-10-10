package build.wallet.bitkey.f8e

/**
 * Represents f8e (server) spending keyset.
 *
 * @property keysetId remote id of the keyset.
 * @property spendingPublicKey spending f8e dpub.
 * @property serverIntegritySignature present for private keysets, null for legacy.
 */
data class F8eSpendingKeyset(
  val keysetId: String,
  val spendingPublicKey: F8eSpendingPublicKey,
  val serverIntegritySignature: String? = null,
) {
  /**
   * Whether this is a private keyset (server-blind via chaincode delegation).
   */
  val isPrivate: Boolean
    get() = serverIntegritySignature != null
}
