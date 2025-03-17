package build.wallet.bitcoin.keys

/**
 * Represents full bitcoin extended key (public and private) for segwit wallet (BIP-84).
 */
data class DescriptorKeypair(
  val publicKey: DescriptorPublicKey,
  val privateKey: ExtendedPrivateKey,
)
