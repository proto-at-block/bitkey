package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddressParser
import build.wallet.bitcoin.address.BitcoinAddressParser.BitcoinAddressParserError
import build.wallet.bitcoin.address.BitcoinAddressParser.BitcoinAddressParserError.InvalidNetwork
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder.BitcoinInvoiceUrlEncoderError
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder.BitcoinInvoiceUrlEncoderError.InvalidAddress
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder.BitcoinInvoiceUrlEncoderError.InvalidUri
import build.wallet.bitcoin.invoice.ParsedPaymentData.BIP21
import build.wallet.bitcoin.invoice.ParsedPaymentData.Lightning
import build.wallet.bitcoin.invoice.ParsedPaymentData.Onchain
import build.wallet.bitcoin.invoice.PaymentDataParser.PaymentDataParserError
import build.wallet.bitcoin.invoice.PaymentDataParser.PaymentDataParserError.InvalidBIP21URI
import build.wallet.bitcoin.invoice.PaymentDataParser.PaymentDataParserError.InvalidLightningInvoice
import build.wallet.bitcoin.lightning.LightningInvoiceParser
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold

class PaymentDataParserImpl(
  private val bip21InvoiceEncoder: BitcoinInvoiceUrlEncoder,
  private val bitcoinAddressParser: BitcoinAddressParser,
  private val lightningInvoiceParser: LightningInvoiceParser,
) : PaymentDataParser {
  override fun decode(
    paymentDataString: String,
    networkType: BitcoinNetworkType,
  ): Result<ParsedPaymentData, PaymentDataParserError> {
    return if (paymentDataString.isBIP21) {
      bip21InvoiceEncoder.decode(
        paymentDataString,
        networkType
      ).fold(
        success = { Ok(BIP21(it)) },
        failure = { Err(it.toPaymentDataParserError()) }
      )
    } else if (paymentDataString.isLightningInvoice) {
      lightningInvoiceParser.parse(paymentDataString)
        ?.let { Ok(Lightning(it)) }
        ?: Err(InvalidLightningInvoice)
    } else {
      bitcoinAddressParser.parse(
        paymentDataString,
        networkType
      ).fold(
        success = { Ok(Onchain(it)) },
        failure = { Err(it.toPaymentDataParserError()) }
      )
    }
  }
}

private fun BitcoinAddressParserError.toPaymentDataParserError(): PaymentDataParserError {
  return when (this) {
    is InvalidNetwork -> PaymentDataParserError.InvalidNetwork
    else -> InvalidBIP21URI
  }
}

private fun BitcoinInvoiceUrlEncoderError.toPaymentDataParserError(): PaymentDataParserError {
  return when (this) {
    is InvalidUri -> InvalidBIP21URI
    is InvalidAddress -> parserError.toPaymentDataParserError()
  }
}

private val String.isBIP21: Boolean
  get() = startsWith("bitcoin:", ignoreCase = true)

private val String.isLightningInvoice: Boolean
  get() = startsWith("ln", ignoreCase = true)
