package build.wallet.statemachine.data.keybox.transactions

import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.transactions.BitcoinTransactionFake
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData.FullAccountTransactionsLoadedData

val KeyboxTransactionsDataMock =
  FullAccountTransactionsLoadedData(
    balance = BitcoinBalanceFake(confirmed = BitcoinMoney.sats(100_000)),
    transactions = immutableListOf(BitcoinTransactionFake),
    unspentOutputs = immutableListOf(
      BdkUtxo(
        outPoint = BdkOutPoint("def", 0u),
        txOut = BdkTxOut(
          value = 100_000u,
          scriptPubkey = BdkScriptMock()
        ),
        isSpent = false
      )
    ),
    syncTransactions = {}
  )
