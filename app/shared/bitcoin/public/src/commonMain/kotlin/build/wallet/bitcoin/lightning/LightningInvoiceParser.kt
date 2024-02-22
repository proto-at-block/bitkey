package build.wallet.bitcoin.lightning

/**
 * Interface for parsing a
 * [BOLT 11](https://github.com/lightning/bolts/blob/master/11-payment-encoding.md) invoice
 */
interface LightningInvoiceParser {
  fun parse(invoiceString: String): LightningInvoice?
}
