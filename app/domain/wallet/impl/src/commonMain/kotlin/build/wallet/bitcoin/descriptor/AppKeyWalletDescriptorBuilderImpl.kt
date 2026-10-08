package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Watching
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class AppKeyWalletDescriptorBuilderImpl : AppKeyWalletDescriptorBuilder {
  override fun receivingDescriptor(appPrivateKey: ExtendedPrivateKey): Spending {
    return Spending("wpkh(${appPrivateKey.xprv.withChild(isChange = false)})")
  }

  override fun changeDescriptor(appPrivateKey: ExtendedPrivateKey): Spending {
    return Spending("wpkh(${appPrivateKey.xprv.withChild(isChange = true)})")
  }

  override fun watchingDescriptor(
    publicKey: DescriptorPublicKey,
    isChange: Boolean,
  ): Watching = Watching("wpkh(${publicKey.dpub.withChild(isChange = isChange)})")

  /**
   * Appends the receiving (`0`) or change (`1`) normal (non-hardened) child and a wildcard.
   *
   * Mirrors [BitcoinMultiSigDescriptorBuilderImpl]'s normalization so that key expressions are
   * accepted in any of the shapes our key generation and backups produce:
   * - already terminated (`.../0/\*` or `.../1/\*`) -> normalized to the requested chain
   * - wildcard only (`.../\*`) -> the chain index is spliced in before the wildcard
   * - bare account key (`...xprv`) -> the full `/<chain>/\*` suffix is appended
   *
   * The bare-key case is the important difference from the multisig builder, which always receives
   * keys that already carry a wildcard. An account key without a wildcard would otherwise yield a
   * descriptor that derives exactly one address, silently under-scanning the app-key wallet.
   */
  private fun String.withChild(isChange: Boolean): String {
    val receivingOrChange = if (isChange) "1" else "0"
    val accountKey = removeSuffix("/0/*").removeSuffix("/1/*")
    return when {
      accountKey.endsWith("/*") -> accountKey.removeSuffix("/*") + "/$receivingOrChange/*"
      else -> "$accountKey/$receivingOrChange/*"
    }
  }
}
