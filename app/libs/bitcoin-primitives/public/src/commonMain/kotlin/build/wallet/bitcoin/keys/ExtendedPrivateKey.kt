package build.wallet.bitcoin.keys

import dev.zacsweers.redacted.annotations.Redacted

/**
 * Represents a private extended bitcoin key derived for segwit wallet (BIP-84).
 */
@Redacted
data class ExtendedPrivateKey(
  val xprv: String,
  val mnemonic: String,
) {
  init {
    require(xprv.isNotBlank())
    require(mnemonic.isNotBlank())
  }
}
