package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.invoice.PaymentDataParser.PaymentDataParserError
import build.wallet.bitcoin.lightning.LightningInvoice
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class PaymentDataParserMock(
  val validBip21URIs: MutableSet<String> = mutableSetOf(),
  val validBip21URIsWithAmount: MutableSet<String> = mutableSetOf(),
  val validAddresses: MutableSet<String> = mutableSetOf(),
  val validBOLT11Invoices: MutableSet<String> = mutableSetOf(),
) : PaymentDataParser {
  override fun decode(
    paymentDataString: String,
    networkType: BitcoinNetworkType,
  ): Result<ParsedPaymentData, PaymentDataParserError> {
    return calculatePaymentData(paymentDataString)?.let {
      Ok(it)
    } ?: Err(PaymentDataParserError.InvalidNetwork)
  }

  private fun calculatePaymentData(paymentDataString: String): ParsedPaymentData? {
    return if (validBip21URIs.contains(paymentDataString)) {
      ParsedPaymentData.BIP21(
        BIP21PaymentData(
          onchainInvoice = validBitcoinInvoice,
          lightningInvoice = null
        )
      )
    } else if (validBip21URIsWithAmount.contains(paymentDataString)) {
      ParsedPaymentData.BIP21(
        BIP21PaymentData(
          onchainInvoice = validBitcoinInvoiceWithAmount,
          lightningInvoice = null
        )
      )
    } else if (validAddresses.contains(paymentDataString)) {
      ParsedPaymentData.Onchain(BitcoinAddress(paymentDataString))
    } else if (validBOLT11Invoices.contains(paymentDataString)) {
      ParsedPaymentData.Lightning(
        LightningInvoice(
          payeePubKey = null,
          paymentHash = null,
          isExpired = false,
          amountMsat = null
        )
      )
    } else {
      null
    }
  }
}
