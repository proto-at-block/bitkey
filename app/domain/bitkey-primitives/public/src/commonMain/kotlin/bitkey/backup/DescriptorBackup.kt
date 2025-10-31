package bitkey.backup

import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.encrypt.XCiphertext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wallet descriptor encrypted with hardware. The backup is meant to be uploaded
 * and retrieved through f8e.
 *
 * @property keysetId id of a [SpendingKeyset] associated with a descriptor.
 * @property sealedDescriptor wallet descriptor encrypted with hardware.
 * @property privateWalletRootXpub server root xpub - only present for private wallets, used to calculate psbt tweaks
 */
@Serializable
data class DescriptorBackup(
  @SerialName("keyset_id")
  val keysetId: String,
  @SerialName("sealed_descriptor")
  val sealedDescriptor: XCiphertext,
  @SerialName("sealed_server_root_xpub")
  val privateWalletRootXpub: XCiphertext?,
)
