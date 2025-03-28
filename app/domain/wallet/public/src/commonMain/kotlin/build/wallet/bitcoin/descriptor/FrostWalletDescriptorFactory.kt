package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.wallet.SpendingWalletDescriptor
import build.wallet.bitcoin.wallet.WatchingWalletDescriptor
import build.wallet.bitkey.keybox.SoftwareKeybox

interface FrostWalletDescriptorFactory {
  fun watchingWalletDescriptor(softwareKeybox: SoftwareKeybox): WatchingWalletDescriptor

  fun spendingWalletDescriptor(softwareKeybox: SoftwareKeybox): SpendingWalletDescriptor
}
