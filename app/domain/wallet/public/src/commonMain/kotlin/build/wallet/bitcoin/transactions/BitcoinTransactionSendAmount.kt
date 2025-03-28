package build.wallet.bitcoin.transactions

import build.wallet.money.BitcoinMoney

sealed interface BitcoinTransactionSendAmount {
  data class ExactAmount(val money: BitcoinMoney) : BitcoinTransactionSendAmount

  // Drain wallet.
  data object SendAll : BitcoinTransactionSendAmount
}
