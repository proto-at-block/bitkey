package build.wallet.bitcoin.fees

import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.money.BitcoinMoney

class BitcoinTransactionBaseCalculatorImpl : BitcoinTransactionBaseCalculator {
  override fun minimumSatsRequiredForTransaction(
    walletBalance: BitcoinBalance,
    sendAmount: BitcoinTransactionSendAmount,
    fees: Map<EstimatedTransactionPriority, Fee>,
  ): BitcoinMoney {
    return when (sendAmount) {
      is ExactAmount -> sendAmount.money + (fees[SIXTY_MINUTES]?.amount ?: BitcoinMoney.zero())
      is SendAll -> walletBalance.total - (fees[FASTEST]?.amount ?: BitcoinMoney.zero())
    }
  }
}
