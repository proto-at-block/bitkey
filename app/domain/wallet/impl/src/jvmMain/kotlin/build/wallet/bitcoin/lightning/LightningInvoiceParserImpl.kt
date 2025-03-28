package build.wallet.bitcoin.lightning

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class LightningInvoiceParserImpl : LightningInvoiceParser {
  override fun parse(invoiceString: String): LightningInvoice? {
    TODO("Not implemented")
  }
}
