package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Watching
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey

/**
 * Fake [AppKeyWalletDescriptorBuilder] producing deterministic, obviously-fake descriptors.
 *
 * Keeps the `wpkh(...)` shape and the `/0/\*` vs `/1/\*` chain distinction so callers can assert that
 * the receiving and change descriptors were not swapped, without depending on real key expressions.
 */
class AppKeyWalletDescriptorBuilderFake : AppKeyWalletDescriptorBuilder {
  override fun receivingDescriptor(appPrivateKey: ExtendedPrivateKey): Spending {
    return Spending("wpkh(${appPrivateKey.xprv}/0/*)")
  }

  override fun changeDescriptor(appPrivateKey: ExtendedPrivateKey): Spending {
    return Spending("wpkh(${appPrivateKey.xprv}/1/*)")
  }

  override fun watchingDescriptor(
    publicKey: DescriptorPublicKey,
    isChange: Boolean,
  ): Watching {
    val chain = if (isChange) "1" else "0"
    return Watching("wpkh(${publicKey.dpub}/$chain/*)")
  }
}
