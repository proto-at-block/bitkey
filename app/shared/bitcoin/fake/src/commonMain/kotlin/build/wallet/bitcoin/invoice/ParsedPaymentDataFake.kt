package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.address.someBitcoinAddress

val validParsedBitcoinInvoice =
  ParsedPaymentData.BIP21(
    bip21PaymentData =
      BIP21PaymentData(
        onchainInvoice =
          BitcoinInvoice(
            address = someBitcoinAddress
          ),
        lightningInvoice = null
      )
  )

val validParsedBitcoinAddress =
  ParsedPaymentData.Onchain(
    bitcoinAddress = someBitcoinAddress
  )
