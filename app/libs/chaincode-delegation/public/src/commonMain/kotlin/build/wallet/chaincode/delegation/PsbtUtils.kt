package build.wallet.chaincode.delegation

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitcoin.transactions.Psbt

/**
 * Utility interface for computing and populating tweaks into Partially Signed Bitcoin Transactions
 * for Chaincode Delegation.
 */
interface PsbtUtils {
  /*
   * Computes and populates the necessary tweaks into the provided PSBT.
   *
   * @param psbt The psbt to be modified.
   * @param appAccountDprv The app's account-level descriptor private key (dprv)
   *        the type/name mismatch is because we have always stored the dprv in the ExtendedPrivateKey type
   *        (they are both just string wrappers really)
   * @param serverRootXpub The server root extended public key (xpub) which the App maintains by
   * combining a chain code it generates with a raw public key returned from the server.
   * @param hwAccountDpub The hardware wallet's account-level extended public key (xpub).
   */
  fun psbtWithTweaks(
    psbt: Psbt,
    appAccountDprv: ExtendedPrivateKey,
    serverRootXpub: String,
    hwAccountDpub: DescriptorPublicKey,
  ): ChaincodeDelegationResult<String>
}
