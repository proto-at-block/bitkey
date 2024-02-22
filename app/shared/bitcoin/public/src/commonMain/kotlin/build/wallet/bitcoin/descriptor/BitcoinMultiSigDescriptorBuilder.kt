package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey

interface BitcoinMultiSigDescriptorBuilder {
  /**
   * Created 2-of-3 spending (with app private key) multisig descriptor for receiving.
   * Includes the private key of the app, meaning the app can sign transactions
   * associated with descriptor.
   * https://bitcoindevkit.org/descriptors/
   */
  fun spendingReceivingDescriptor(
    appPrivateKey: ExtendedPrivateKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): BitcoinDescriptor.Spending

  /**
   * Created 2-of-3 watching (without app private key) multisig descriptor for tracking.
   * Does not include the private key of the app, meaning the app cannot sign transactions
   * associated with descriptor, only read them.
   */
  fun watchingReceivingDescriptor(
    appPublicKey: DescriptorPublicKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): BitcoinDescriptor.Watching

  /**
   * Created 2-of-3 spending (with app private key) multisig descriptor for change.
   * Includes the private key of the app, meaning the app can sign transactions
   * associated with descriptor.
   * https://bitcoindevkit.org/descriptors/
   */
  fun spendingChangeDescriptor(
    appPrivateKey: ExtendedPrivateKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): BitcoinDescriptor.Spending

  /**
   * Created 2-of-3 watching (with app public key) multisig descriptor for change.
   * Does not include the private key of the app, meaning the app cannot sign transactions
   * associated with descriptor, only read them.
   * https://bitcoindevkit.org/descriptors/
   */
  fun watchingChangeDescriptor(
    appPublicKey: DescriptorPublicKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): BitcoinDescriptor.Watching
}
