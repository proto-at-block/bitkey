package build.wallet.bitcoin.lightning

import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapError
import build.wallet.rust.core.Invoice as FFILightningInvoice

@BitkeyInject(AppScope::class)
class LightningInvoiceParserImpl : LightningInvoiceParser {
  override fun parse(invoiceString: String): LightningInvoice? {
    return catchingResult {
      FFILightningInvoice(invoiceString).lightningInvoice
    }.mapError {
      logWarn(throwable = it) { "Error parsing invoice: $invoiceString" }
      null
    }.get()
  }
}

private val FFILightningInvoice.lightningInvoice: LightningInvoice
  get() =
    LightningInvoice(
      paymentHash = paymentHash(),
      payeePubKey = payeePubkey(),
      isExpired = isExpired(),
      amountMsat = amountMsat()
    )
