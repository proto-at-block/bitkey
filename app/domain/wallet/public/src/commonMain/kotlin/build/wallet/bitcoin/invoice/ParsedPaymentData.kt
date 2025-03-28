package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.lightning.LightningInvoice

// Data structure representing the different permutations that `PaymentDataParser` could return
sealed interface ParsedPaymentData {
  // For when user enters `bitcoin:BC1QYL...`
  data class BIP21(val bip21PaymentData: BIP21PaymentData) : ParsedPaymentData

  // For when user enters _just_ `LNBC10U1...`
  data class Lightning(val lightningInvoice: LightningInvoice) : ParsedPaymentData

  // For when user enters _just_ `BC1Q...`
  data class Onchain(val bitcoinAddress: BitcoinAddress) : ParsedPaymentData
}
