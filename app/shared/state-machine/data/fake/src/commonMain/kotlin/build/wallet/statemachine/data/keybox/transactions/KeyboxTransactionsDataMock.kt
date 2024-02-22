package build.wallet.statemachine.data.keybox.transactions

import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.transactions.BitcoinTransactionFake
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData.FullAccountTransactionsLoadedData

val KeyboxTransactionsDataMock =
  FullAccountTransactionsLoadedData(
    balance = BitcoinBalanceFake(confirmed = BitcoinMoney.sats(100_000)),
    transactions = immutableListOf(BitcoinTransactionFake),
    syncTransactions = {}
  )
