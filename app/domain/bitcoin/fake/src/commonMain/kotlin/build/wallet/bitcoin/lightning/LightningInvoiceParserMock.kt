package build.wallet.bitcoin.lightning

class LightningInvoiceParserMock(
  val validInvoices: MutableSet<String> = mutableSetOf(),
) : LightningInvoiceParser {
  override fun parse(invoiceString: String): LightningInvoice? =
    when (invoiceString) {
      in validInvoices ->
        LightningInvoice(
          paymentHash = "2097674b02a257fa7fa6b758a88b62cdbae8f856d5b6c3bc36a9bb96501758bc",
          payeePubKey = "037cc5f9f1da20ac0d60e83989729a204a33cc2d8e80438969fadf35c1c5f1233b",
          isExpired = true,
          amountMsat = 1000000u
        )
      else -> null
    }
}
