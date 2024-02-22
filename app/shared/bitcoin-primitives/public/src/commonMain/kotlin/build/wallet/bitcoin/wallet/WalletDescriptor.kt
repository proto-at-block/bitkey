package build.wallet.bitcoin.wallet

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.descriptor.BitcoinDescriptor

/**
 * Represents a Bitcoin wallet descriptor.
 *
 * @property identifier some wallet identifier. This should be unique for each wallet.
 * @property networkType the Bitcoin network type used for this wallet.
 * @property receivingDescriptor the descriptor used to receive funds.
 * @property changeDescriptor the descriptor used to receive change.
 */
interface WalletDescriptor {
  val identifier: String
  val networkType: BitcoinNetworkType
  val receivingDescriptor: BitcoinDescriptor
  val changeDescriptor: BitcoinDescriptor
}
