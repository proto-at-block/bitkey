package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.BitcoinNetworkType
import com.github.michaelbull.result.Result

interface PaymentDataParser {
  /**
   * Accepts an arbitrary string and returns `ParsedPaymentData`, expressing the type of payment
   * it was able to infer.
   *
   * Returns null if the string isn't either:
   *   - a valid BTC address string
   *   - a valid BIP-21 URI string
   *   - a valid BOLT 11 invoice
   */
  fun decode(
    paymentDataString: String,
    networkType: BitcoinNetworkType,
  ): Result<ParsedPaymentData, PaymentDataParserError>

  sealed class PaymentDataParserError : Error() {
    data object InvalidNetwork : PaymentDataParserError()

    data object InvalidBIP21URI : PaymentDataParserError()

    data object InvalidLightningInvoice : PaymentDataParserError()
  }
}
