package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddressParser.BitcoinAddressParserError
import com.github.michaelbull.result.Result

interface BitcoinInvoiceUrlEncoder {
  /**
   * Decodes a string to a [BitcoinInvoice].
   *
   * Returns null if the string isn't either:
   *   - a valid BTC address string
   *   - a valid BIP-21 URI string
   */
  fun decode(
    urlString: String,
    networkType: BitcoinNetworkType,
  ): Result<BIP21PaymentData, BitcoinInvoiceUrlEncoderError>

  /**
   * Encodes the [BitcoinInvoice] to a BIP-21 URI string
   * See: [BIP-21](https://en.bitcoin.it/wiki/BIP_0021)
   */
  fun encode(invoice: BitcoinInvoice): String

  sealed class BitcoinInvoiceUrlEncoderError : Error() {
    data class InvalidAddress(
      val parserError: BitcoinAddressParserError,
    ) : BitcoinInvoiceUrlEncoderError()

    data object InvalidUri : BitcoinInvoiceUrlEncoderError()
  }
}
