package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.address.bitcoinAddressP2PKH
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.money.BitcoinMoney

val validBitcoinInvoice =
  BitcoinInvoice(
    address = someBitcoinAddress
  )

val validBitcoinInvoiceWithAmount =
  BitcoinInvoice(
    address = bitcoinAddressP2PKH,
    amount = BitcoinMoney.btc(200.0)
  )
