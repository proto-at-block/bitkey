package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Watching
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey

interface BitcoinMultiSigDescriptorBuilder {
  /**
   * Re-build a descriptor for receiving keysets, replacing the public key.
   *
   * @param descriptorKeyset The pre-built descriptor keyset to modify.
   * @param publicKey The public key, found in the descriptor, that should be replaced.
   * @param privateKey The private key to replace the public key.
   */
  fun spendingReceivingDescriptor(
    descriptorKeyset: String,
    publicKey: DescriptorPublicKey,
    privateKey: ExtendedPrivateKey,
  ): BitcoinDescriptor.Spending

  /**
   * Re-build a descriptor for change keysets, replacing the public key.
   *
   * @param descriptorKeyset The pre-built descriptor keyset to modify.
   * @param publicKey The public key, found in the descriptor, that should be replaced.
   * @param privateKey The private key to replace the public key.
   */
  fun spendingChangeDescriptor(
    descriptorKeyset: String,
    publicKey: DescriptorPublicKey,
    privateKey: ExtendedPrivateKey,
  ): BitcoinDescriptor.Spending

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

  /**
   * Created 2-of-3 watching (with app public key) change-level multisig descriptor.
   * Does not include the private key of the app, meaning the app cannot sign transactions
   * associated with descriptor, only read them.
   *
   * Note: This is derived up to 84'/0'/0', and wildcarded at the change-level of the
   * key tree (e.g. [fp/84h/0h/0h]xpub/\*). A more precise wallet descriptor would be
   * [fp/84h/0h/0h]xpub/0;1/\*, especially for exposure externally.
   *
   * https://bitcoindevkit.org/descriptors/
   */
  fun watchingDescriptor(
    appPublicKey: DescriptorPublicKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Watching
}
