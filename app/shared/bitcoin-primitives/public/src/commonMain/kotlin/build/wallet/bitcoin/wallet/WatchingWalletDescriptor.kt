package build.wallet.bitcoin.wallet

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.descriptor.BitcoinDescriptor

/**
 * Represents a Bitcoin wallet descriptor for spending funds.
 *
 * @property identifier some wallet identifier. This should be unique for each wallet.
 * @property networkType the Bitcoin network type used for this wallet.
 * @property receivingDescriptor the descriptor used to receive funds.
 * @property changeDescriptor the descriptor used to receive change.
 */
data class WatchingWalletDescriptor(
  override val identifier: String,
  override val networkType: BitcoinNetworkType,
  override val receivingDescriptor: BitcoinDescriptor.Watching,
  override val changeDescriptor: BitcoinDescriptor.Watching,
) : WalletDescriptor
