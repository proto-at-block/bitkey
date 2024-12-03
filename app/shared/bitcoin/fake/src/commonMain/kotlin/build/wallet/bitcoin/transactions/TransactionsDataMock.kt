package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.utxo.Utxos
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.USD

val TransactionsDataMock = TransactionsData(
  balance = BitcoinBalanceFake(confirmed = BitcoinMoney.sats(100_000)),
  fiatBalance = FiatMoney.zero(USD),
  transactions = immutableListOf(BitcoinTransactionFake),
  utxos = Utxos(
    confirmed = setOf(
      BdkUtxo(
        outPoint = BdkOutPoint("def", 0u),
        txOut = BdkTxOut(
          value = 100_000u,
          scriptPubkey = BdkScriptMock()
        ),
        isSpent = false
      )
    ),
    unconfirmed = emptySet()
  )
)
