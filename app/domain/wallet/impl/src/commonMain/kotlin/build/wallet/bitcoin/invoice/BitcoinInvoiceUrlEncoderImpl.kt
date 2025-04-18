package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddressParser
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder.BitcoinInvoiceUrlEncoderError
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder.BitcoinInvoiceUrlEncoderError.InvalidAddress
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder.BitcoinInvoiceUrlEncoderError.InvalidUri
import build.wallet.bitcoin.lightning.LightningInvoiceParser
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder

@BitkeyInject(AppScope::class)
class BitcoinInvoiceUrlEncoderImpl(
  private val bitcoinAddressParser: BitcoinAddressParser,
  private val lightningInvoiceParser: LightningInvoiceParser,
) : BitcoinInvoiceUrlEncoder {
  override fun decode(
    urlString: String,
    networkType: BitcoinNetworkType,
  ): Result<BIP21PaymentData, BitcoinInvoiceUrlEncoderError> {
    // Check if a scheme exists
    val uriSchemeBitcoin = "bitcoin:"
    if (!urlString.lowercase().startsWith(uriSchemeBitcoin)) {
      return Err(InvalidUri)
    }

    // Otherwise, parse as a URI.
    val url = URLBuilder(urlString = urlString).build()

    // Parse the address. Return null if it doesn't parse.
    val addressString = url.pathSegments.firstOrNull() ?: return Err(InvalidUri)
    val address = bitcoinAddressParser.parse(addressString, networkType)
      .getOrElse { error ->
        return Err(InvalidAddress(error))
      }

    // Check if a lightning parameter exists. Assign to null if doesn't parse.
    val lightningInvoice =
      url.parameters.get("lightning")?.let { lightningInvoiceString ->
        lightningInvoiceParser.parse(lightningInvoiceString)
      }

    // Parse the parameters
    var amount: Double? = null
    var label: String? = null
    var message: String? = null
    val customParameters = mutableMapOf<String, String>()

    for (entry in url.parameters.entries()) {
      // Multiple values for a parameter are unexpected. Treat it as an invalid URI.
      if (entry.value.size > 1) return Err(InvalidUri)

      // BIP-21 specifies that if an unsupported field with a `req-` prefix is encountered,
      // that the URI should be considered invalid.
      if (entry.key.startsWith("req-", true)) return Err(InvalidUri)

      when (entry.key.lowercase()) {
        "amount" -> amount = entry.value.firstOrNull()?.toDoubleOrNull() // TODO add validation
        "label" -> label = entry.value.firstOrNull()
        "message" -> message = entry.value.firstOrNull()
        else -> entry.value.firstOrNull()?.let { customParameters[entry.key] = it }
      }
    }

    val onchainInvoice =
      BitcoinInvoice(
        address = address,
        amount =
          amount?.let {
            BitcoinMoney.btc(amount.toBigDecimal())
          },
        label = label,
        message = message,
        customParameters = customParameters.takeIf { it.isNotEmpty() }
      )

    return Ok(
      BIP21PaymentData(
        onchainInvoice = onchainInvoice,
        lightningInvoice = lightningInvoice
      )
    )
  }

  override fun encode(invoice: BitcoinInvoice): String {
    val parameters =
      Parameters.build {
        invoice.amount?.let { append("amount", it.value.toPlainString()) }
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
