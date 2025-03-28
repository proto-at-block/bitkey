package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder.BitcoinInvoiceUrlEncoderError
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder.BitcoinInvoiceUrlEncoderError.InvalidUri
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.http.URLBuilder
import io.ktor.http.parameters

class BitcoinInvoiceUrlEncoderMock(
  private val validUrlStringsWithAmount: List<String> = emptyList(),
  private val validUrlStringsWithoutAmount: List<String> = emptyList(),
) : BitcoinInvoiceUrlEncoder {
  override fun decode(
    urlString: String,
    networkType: BitcoinNetworkType,
  ): Result<BIP21PaymentData, BitcoinInvoiceUrlEncoderError> {
    return if (validUrlStringsWithAmount.contains(urlString)) {
      Ok(
        BIP21PaymentData(
          onchainInvoice = validBitcoinInvoiceWithAmount,
          lightningInvoice = null
        )
      )
    } else if (validUrlStringsWithoutAmount.contains(urlString)) {
      Ok(
        BIP21PaymentData(
          onchainInvoice = validBitcoinInvoice,
          lightningInvoice = null
        )
      )
    } else {
      Err(InvalidUri)
    }
  }

  override fun encode(invoice: BitcoinInvoice): String {
    val parameters =
      parameters {
        invoice.amount?.let { append("amount", it.value.toString()) }
        invoice.label?.let { append("label", it) }
        invoice.message?.let { append("message", it) }
        invoice.customParameters?.forEach { (name, value) ->
          append(name, value)
        }
      }

    val url =
      URLBuilder(
        pathSegments = listOf(invoice.address.address),
        parameters = parameters
      ).build()

    // Remove the first character of [encodedPathAndQuery] because it is a slash "/".
    return "bitcoin:${url.encodedPathAndQuery.drop(1)}"
  }
}
