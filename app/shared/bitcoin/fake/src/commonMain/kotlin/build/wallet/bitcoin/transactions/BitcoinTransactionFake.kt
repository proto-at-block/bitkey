package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bitcoin.BlockTime
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.money.BitcoinMoney
import build.wallet.time.someInstant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration

val defaultTransactionWeight = 325UL

val BitcoinTransactionFake =
  BitcoinTransaction(
    id = "c4f5835c0b77d438160cf54c4355208b0a39f58919ff4c221df6ebedc1ad67be",
    recipientAddress = someBitcoinAddress,
    broadcastTime = someInstant,
    estimatedConfirmationTime = someInstant.plus(10.toDuration(DurationUnit.MINUTES)),
    confirmationStatus = Pending,
    total = BitcoinMoney.btc(1.0),
    subtotal = BitcoinMoney.btc(1.0),
    fee = null,
    weight = defaultTransactionWeight,
    vsize = defaultTransactionWeight / 4UL,
    incoming = true,
    inputs = emptyImmutableList(),
    outputs = emptyImmutableList()
  )

fun BitcoinTransactionMock(
  total: BitcoinMoney,
  fee: BitcoinMoney? = null,
  incoming: Boolean = false,
  confirmationTime: Instant?,
  inputs: ImmutableList<BdkTxIn> = emptyImmutableList(),
  outputs: ImmutableList<BdkTxOut> = emptyImmutableList(),
): BitcoinTransaction =
  BitcoinTransaction(
    id = "some-id",
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
    incoming = incoming,
    inputs = inputs,
    outputs = outputs
  )
