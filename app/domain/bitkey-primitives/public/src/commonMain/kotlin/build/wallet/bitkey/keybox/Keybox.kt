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
 * @property keysets All spending keysets for this keybox, including the active one and any inactive ones
 * @property canUseKeyboxKeysets whether the [keysets] are complete, containing all active and inactive keysets.
 *           This is required as this field was not properly maintained in the past, so some users only have
 *           their most recently active keyset available.
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
  val canUseKeyboxKeysets: Boolean,
) {
  init {
    val matchingKeyset = keysets.find { it == activeSpendingKeyset }
    require(keysets.isNotEmpty() && matchingKeyset != null) {
      "activeSpendingKeyset must be present in keysets!"
    }
  }

  /**
   * Whether this keybox uses a private keyset (server-blind via chaincode delegation).
   */
  val isPrivate: Boolean
    get() = activeSpendingKeyset.isPrivate
}
