package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.wallet.SpendingWalletDescriptor
import build.wallet.bitcoin.wallet.WatchingWalletDescriptor
import build.wallet.bitkey.keybox.SoftwareKeybox

class FrostWalletDescriptorFactoryFake : FrostWalletDescriptorFactory {
  override fun watchingWalletDescriptor(softwareKeybox: SoftwareKeybox): WatchingWalletDescriptor {
    return WatchingWalletDescriptor(
      identifier = softwareKeybox.id,
      networkType = SIGNET,
      receivingDescriptor = Spending("receiving").toWatchingDescriptor(),
      changeDescriptor = Spending("change").toWatchingDescriptor()
    )
  }

  override fun spendingWalletDescriptor(softwareKeybox: SoftwareKeybox): SpendingWalletDescriptor {
    return SpendingWalletDescriptor(
      identifier = softwareKeybox.id,
      networkType = SIGNET,
      receivingDescriptor = Spending("receiving"),
      changeDescriptor = Spending("change")
    )
  }

  private fun Spending.toWatchingDescriptor() = BitcoinDescriptor.Watching(raw)
}
