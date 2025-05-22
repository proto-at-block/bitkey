package build.wallet.bitcoin.address

import dev.zacsweers.redacted.annotations.Redacted

/**
 * Represents a Bitcoin address, supported types:
 * - Legacy (P2PKH), example "15e15hWo6CShMgbAfo8c2Ykj4C6BLq6Not"
 * - Pay-to-Script-Hash (P2SH), example "35PBEaofpUeH8VnnNSorM1QZsadrZoQp4N"
 * - Native SegWit (35PBEaofpUeH8VnnNSorM1QZsadrZoQp4N), example "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
 * - Taproot (P2TR), example "bc1pmzfrwwndsqmk5yh69yjr5lfgfg4ev8c0tsc06e"
 */
@Redacted
data class BitcoinAddress(val address: String) {
  fun chunkedAddress(): String =
    address.let {
      address.chunked(4).joinToString(separator = " ")
    }

  fun truncatedAddress(): String =
    address.let {
      "${it.take(4)}...${it.takeLast(4)}"
    }
}
