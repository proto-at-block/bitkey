package build.wallet.bitkey.keybox

import bitkey.account.FullAccountConfig
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.spending.SpendingKeyset

/**
 * A keybox is a collection of keysets.
 * This keybox holds [SpendingKeyset] objects, which are used for signing transactions and querying transaction lists
 * (and by extension balance) for transactions signed by that set of keys.
 *
 * @property localId Unique local identifier for this keybox. Generated at the time of keybox's
 * creation or restoration on this app installation.
 * @property fullAccountId
 * @property activeSpendingKeyset The current, active keyset which is being used by the application to track main balance, transaction
 * history, sign and receive transactions, etc.
 * @property config Defines configuration of this keybox. All [SpendingKeyset]s in the
 * keybox will return the same network type as in the [config]'s, but there is currently no
 * validation that the keys within those keysets correspond to this network type – see [W-877].
 * @param appGlobalAuthKeyHwSignature the active app global auth key signed with the active
 * hardware's auth key. Used to verify the authenticity of the Social Recovery contacts as part
 * of SPAKE protocol.
 */
data class Keybox(
  val localId: String,
  val config: FullAccountConfig,
  val fullAccountId: FullAccountId,
  val activeSpendingKeyset: SpendingKeyset,
  val activeAppKeyBundle: AppKeyBundle,
  val activeHwKeyBundle: HwKeyBundle,
  val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  val keysets: List<SpendingKeyset>,
)
