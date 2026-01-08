package build.wallet.bitcoin.address

/**
 * Error indicating that address generation was blocked due to a keyset mismatch
 * between local and server state.
 *
 * This error occurs when a user has recovered from a stale cloud backup and their
 * local `activeSpendingKeyset` doesn't match the server's active keyset. Generating
 * addresses in this state could result in funds being sent to addresses the user
 * may not control.
 */
data class KeysetMismatchError(
  override val message: String,
  val localKeysetId: String,
  val serverKeysetId: String,
) : Error(message)
