package build.wallet.statemachine.send.fee

import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.fees.BitcoinTransactionBaseCalculator
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.money.BitcoinMoney

class BitcoinTransactionBaseCalculatorMock(
  var minimumSatsRequired: BitcoinMoney,
) : BitcoinTransactionBaseCalculator {
  override fun minimumSatsRequiredForTransaction(
    walletBalance: BitcoinBalance,
    sendAmount: BitcoinTransactionSendAmount,
    fees: Map<EstimatedTransactionPriority, Fee>,
  ): BitcoinMoney = minimumSatsRequired
}
