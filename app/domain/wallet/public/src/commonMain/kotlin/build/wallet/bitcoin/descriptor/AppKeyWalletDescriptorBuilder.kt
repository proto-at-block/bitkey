package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey

/**
 * Builds native-SegWit (BIP-84, `wpkh`) *single-sig* descriptors from an app extended private key.
 *
 * This is intentionally separate from [BitcoinMultiSigDescriptorBuilder]: the 2-of-3 builder always
 * produces `wsh(sortedmulti(2,...))` descriptors that require a second signature (hardware or
 * server). The descriptors produced here are spendable by the app key *alone*, so they are only
 * appropriate for the app-key wallet, where the app key intentionally controls funds without
 * a hardware or server co-signature.
 *
 * Security notes:
 * - Every descriptor returned by [receivingDescriptor] / [changeDescriptor] embeds the xprv and is
 *   therefore [BitcoinDescriptor.Spending]. Never log, persist, or transmit the raw value.
 * - Only [watchingDescriptor] is safe to share (e.g. with a server for scanning), and it still
 *   reveals the wallet's full address history, so treat it as private customer data.
 */
interface AppKeyWalletDescriptorBuilder {
  /**
   * Builds a `wpkh` spending descriptor for the receiving (external, `/0/\*`) key chain.
   *
   * The key expression is normalized so the result always ends in `/0/\*`, regardless of whether
   * [appPrivateKey] already carries a child path or wildcard. Without a wildcard BDK would only
   * derive a single address and a full scan would miss later addresses.
   */
  fun receivingDescriptor(appPrivateKey: ExtendedPrivateKey): BitcoinDescriptor.Spending

  /**
   * Builds a `wpkh` spending descriptor for the change (internal, `/1/\*`) key chain.
   *
   * BDK requires a change descriptor to construct a two-keychain wallet, and prior spending may
   * have left funds on the change chain.
   */
  fun changeDescriptor(appPrivateKey: ExtendedPrivateKey): BitcoinDescriptor.Spending

  /**
   * Builds a `wpkh` watch-only descriptor from a typed extended public key.
   *
   * Contains no private key material, so it can be used to scan for funds before the app takes on
   * the risk of materializing an xprv-bearing descriptor.
   *
   * @param isChange when true, targets the change (`/1/\*`) chain, otherwise receiving (`/0/\*`).
   */
  fun watchingDescriptor(
    publicKey: DescriptorPublicKey,
    isChange: Boolean = false,
  ): BitcoinDescriptor.Watching
}
