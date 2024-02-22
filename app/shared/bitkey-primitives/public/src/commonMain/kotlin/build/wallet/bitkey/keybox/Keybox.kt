package build.wallet.bitkey.keybox

import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.spending.SpendingKeyset
import kotlinx.collections.immutable.ImmutableList

/**
 * A keybox is a collection of keysets.
 * This keybox holds [SpendingKeyset] objects, which are used for signing transactions and querying transaction lists
 * (and by extension balance) for transactions signed by that set of keys.
 *
 * @property localId Unique local identifier for this keybox. Generated at the time of keybox's
 * creation or restoration on this app installation.
 * @property activeSpendingKeyset The current, active keyset which is being used by the application to track main balance, transaction
 * history, sign and receive transactions, etc.
 * @property inactiveKeysets The list of formerly active [SpendingKeyset]s that are now no longer in use.
 * Keysets become inactive during a key rotation which is used during recovery operations.
 * @property config Defines configuration of this keybox. All [SpendingKeyset]s in the
 * keybox will return the same network type as in the [config]'s, but there is currently no
 * validation that the keys within those keysets correspond to this network type – see [W-877].
 */
data class Keybox(
  val localId: String,
  val fullAccountId: FullAccountId,
  val activeSpendingKeyset: SpendingKeyset,
  val inactiveKeysets: ImmutableList<SpendingKeyset>,
  val activeKeyBundle: AppKeyBundle,
  val config: KeyboxConfig,
)
