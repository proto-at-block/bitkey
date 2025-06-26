package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Watching
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class BitcoinMultiSigDescriptorBuilderImpl : BitcoinMultiSigDescriptorBuilder {
  override fun spendingReceivingDescriptor(
    descriptorKeyset: String,
    publicKey: DescriptorPublicKey,
    privateKey: ExtendedPrivateKey,
  ): Spending {
    return Spending(
      descriptorKeyset.replace(publicKey.dpub, privateKey.xprv)
        .substringBeforeLast("#")
        .withReceivingChild()
    )
  }

  override fun spendingChangeDescriptor(
    descriptorKeyset: String,
    publicKey: DescriptorPublicKey,
    privateKey: ExtendedPrivateKey,
  ): Spending {
    return Spending(
      descriptorKeyset.replace(publicKey.dpub, privateKey.xprv)
        .substringBeforeLast("#")
        .withChangeChild()
    )
  }

  override fun spendingReceivingDescriptor(
    appPrivateKey: ExtendedPrivateKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Spending {
    return Spending(
      "wsh(sortedmulti(2,${appPrivateKey.xprv.withReceivingChild()},${hardwareKey.dpub.withReceivingChild()},${serverKey.dpub.withReceivingChild()}))"
    )
  }

  override fun watchingReceivingDescriptor(
    appPublicKey: DescriptorPublicKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Watching {
    return Watching(
      "wsh(sortedmulti(2,${appPublicKey.dpub.withReceivingChild()},${hardwareKey.dpub.withReceivingChild()},${serverKey.dpub.withReceivingChild()}))"
    )
  }

  override fun spendingChangeDescriptor(
    appPrivateKey: ExtendedPrivateKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Spending {
    return Spending(
      "wsh(sortedmulti(2,${appPrivateKey.xprv.withChangeChild()},${hardwareKey.dpub.withChangeChild()},${serverKey.dpub.withChangeChild()}))"
    )
  }

  override fun watchingChangeDescriptor(
    appPublicKey: DescriptorPublicKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Watching {
    return Watching(
      "wsh(sortedmulti(2,${appPublicKey.dpub.withChangeChild()},${hardwareKey.dpub.withChangeChild()},${serverKey.dpub.withChangeChild()}))"
    )
  }

  override fun watchingDescriptor(
    appPublicKey: DescriptorPublicKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Watching {
    return Watching(
      "wsh(sortedmulti(2,${appPublicKey.dpub},${hardwareKey.dpub},${serverKey.dpub}))"
    )
  }

  private fun String.withReceivingChild() = withChild(isChange = false)

  private fun String.withChangeChild() = withChild(isChange = true)

  /**
   * Appends receiving or change normal (non hardened) child.
   *
   * https://learnmeabitcoin.com/technical/derivation-paths
   */
  private fun String.withChild(isChange: Boolean): String {
    val receivingOrChange = if (isChange) "1" else "0"
    return when {
      endsWith("/$receivingOrChange/*") -> this
      else -> this.replace("/*", "/$receivingOrChange/*")
    }
  }
}
