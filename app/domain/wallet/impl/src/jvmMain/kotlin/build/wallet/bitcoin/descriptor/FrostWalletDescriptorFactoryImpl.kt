package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.core.coreFfiNetwork
import build.wallet.bitcoin.wallet.SpendingWalletDescriptor
import build.wallet.bitcoin.wallet.WatchingWalletDescriptor
import build.wallet.bitkey.keybox.SoftwareKeybox
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.rust.core.computeFrostWalletDescriptor

@BitkeyInject(AppScope::class)
class FrostWalletDescriptorFactoryImpl : FrostWalletDescriptorFactory {
  override fun watchingWalletDescriptor(softwareKeybox: SoftwareKeybox): WatchingWalletDescriptor {
    val networkType = softwareKeybox.networkType
    val coreDescriptor = computeFrostWalletDescriptor(
      aggPublicKey = softwareKeybox.shareDetails.keyCommitments.aggregatePublicKey.asString(),
      network = networkType.coreFfiNetwork
    )

    return WatchingWalletDescriptor(
      identifier = softwareKeybox.id,
      networkType = networkType,
      receivingDescriptor = BitcoinDescriptor.Watching("tr(${coreDescriptor.external})"),
      changeDescriptor = BitcoinDescriptor.Watching("tr(${coreDescriptor.change})")
    )
  }

  override fun spendingWalletDescriptor(softwareKeybox: SoftwareKeybox): SpendingWalletDescriptor {
    val networkType = softwareKeybox.networkType
    val coreDescriptor = computeFrostWalletDescriptor(
      aggPublicKey = softwareKeybox.shareDetails.keyCommitments.aggregatePublicKey.asString(),
      network = networkType.coreFfiNetwork
    )

    return SpendingWalletDescriptor(
      identifier = softwareKeybox.id,
      networkType = networkType,
      receivingDescriptor = BitcoinDescriptor.Spending("tr(${coreDescriptor.external})"),
      changeDescriptor = BitcoinDescriptor.Spending("tr(${coreDescriptor.change})")
    )
  }
}
