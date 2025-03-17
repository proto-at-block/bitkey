package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Watching
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey

class BitcoinMultiSigDescriptorBuilderMock : BitcoinMultiSigDescriptorBuilder {
  override fun spendingReceivingDescriptor(
    descriptorKeyset: String,
    publicKey: DescriptorPublicKey,
    privateKey: ExtendedPrivateKey,
  ): Spending {
    return Spending("")
  }

  override fun spendingChangeDescriptor(
    descriptorKeyset: String,
    publicKey: DescriptorPublicKey,
    privateKey: ExtendedPrivateKey,
  ): Spending {
    return Spending("")
  }

  override fun spendingReceivingDescriptor(
    appPrivateKey: ExtendedPrivateKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Spending {
    return Spending("")
  }

  override fun watchingReceivingDescriptor(
    appPublicKey: DescriptorPublicKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Watching {
    return Watching("")
  }

  override fun spendingChangeDescriptor(
    appPrivateKey: ExtendedPrivateKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Spending {
    return Spending("")
  }

  override fun watchingChangeDescriptor(
    appPublicKey: DescriptorPublicKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Watching {
    return Watching("")
  }
}
