package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bitcoin.BlockTime
import build.wallet.bitcoin.BlockTimeFake
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.BitcoinMoney
import build.wallet.time.someInstant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration

val defaultTransactionWeight = 325UL

const val TX_FAKE_ID = "c4f5835c0b77d438160cf54c4355208b0a39f58919ff4c221df6ebedc1ad67be"

val BitcoinTransactionFake =
  BitcoinTransaction(
    id = TX_FAKE_ID,
    recipientAddress = someBitcoinAddress,
    broadcastTime = someInstant,
    estimatedConfirmationTime = someInstant.plus(10.toDuration(DurationUnit.MINUTES)),
    confirmationStatus = Pending,
    total = BitcoinMoney.btc(1.0),
    subtotal = BitcoinMoney.btc(1.0),
    fee = null,
    weight = defaultTransactionWeight,
    vsize = defaultTransactionWeight / 4UL,
    transactionType = Incoming,
    inputs = emptyImmutableList(),
    outputs = emptyImmutableList()
  )

val BitcoinTransactionReceive =
  BitcoinTransaction(
    id = TX_FAKE_ID,
    broadcastTime = null,
    estimatedConfirmationTime = null,
    confirmationStatus =
      Confirmed(
        blockTime = BlockTimeFake
      ),
    recipientAddress = someBitcoinAddress,
    total = BitcoinMoney.btc(1.1),
    subtotal = BitcoinMoney.btc(1.0),
    fee = null,
    weight = 253UL,
    vsize = 63UL,
    transactionType = Incoming,
    inputs = immutableListOf(),
    outputs = immutableListOf()
  )

val BitcoinTransactionSend =
  BitcoinTransaction(
    id = TX_FAKE_ID,
    broadcastTime = someInstant,
    estimatedConfirmationTime = someInstant.plus(10.toDuration(DurationUnit.MINUTES)),
    confirmationStatus =
      Confirmed(
        blockTime = BlockTimeFake
      ),
    recipientAddress = someBitcoinAddress,
    total = BitcoinMoney.btc(1.01000000),
    subtotal = BitcoinMoney.btc(1.0),
    fee = BitcoinMoney.sats(1_000_000),
    weight = 253UL,
    vsize = 63UL,
    transactionType = Outgoing,
    inputs = immutableListOf(),
    outputs = immutableListOf()
  )

val BitcoinTransactionUtxoConsolidation =
  BitcoinTransaction(
    id = TX_FAKE_ID,
    broadcastTime = null,
    estimatedConfirmationTime = null,
    confirmationStatus =
      Confirmed(
        blockTime = BlockTimeFake
      ),
    recipientAddress = someBitcoinAddress,
    total = BitcoinMoney.btc(1.1),
    subtotal = BitcoinMoney.btc(1.0),
    fee = BitcoinMoney.btc(0.1),
    weight = 253UL,
    vsize = 63UL,
    transactionType = UtxoConsolidation,
    inputs = immutableListOf(
      BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList()),
      BdkTxIn(outpoint = BdkOutPoint("def", 0u), sequence = 0u, witness = emptyList())
    ),
    outputs = immutableListOf(
      BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock())
    )
  )

fun BitcoinTransactionMock(
  txid: String = "some-id",
  total: BitcoinMoney,
  fee: BitcoinMoney? = null,
  transactionType: TransactionType = Outgoing,
  confirmationTime: Instant?,
  inputs: ImmutableList<BdkTxIn> = emptyImmutableList(),
  outputs: ImmutableList<BdkTxOut> = emptyImmutableList(),
): BitcoinTransaction =
  BitcoinTransaction(
    id = txid,
    recipientAddress = someBitcoinAddress,
    broadcastTime = someInstant,
    estimatedConfirmationTime = someInstant.plus(10.toDuration(DurationUnit.MINUTES)),
    confirmationStatus =
      if (confirmationTime == null) {
        Pending
      } else {
        Confirmed(
          BlockTime(1, confirmationTime)
        )
      },
    total = total,
    subtotal = total - (fee ?: BitcoinMoney.zero()),
    fee = fee,
    weight = defaultTransactionWeight,
    vsize = defaultTransactionWeight / 4UL,
    transactionType = transactionType,
    inputs = inputs,
    outputs = outputs
  )
