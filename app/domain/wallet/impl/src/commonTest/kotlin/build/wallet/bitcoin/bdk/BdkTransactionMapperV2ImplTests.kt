package build.wallet.bitcoin.bdk

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BlockTime
import build.wallet.bitcoin.address.bitcoinAddressP2TR
import build.wallet.bitcoin.address.bitcoinAddressP2WPKH
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailDaoMock
import build.wallet.bitcoin.wallet.shouldBeIncoming
import build.wallet.bitcoin.wallet.shouldBeOutgoing
import build.wallet.bitcoin.wallet.shouldBePending
import build.wallet.bitcoin.wallet.shouldBeUtxoConsolidation
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.time.someInstant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import uniffi.bdk.Address
import uniffi.bdk.Amount
import uniffi.bdk.BlockHash
import uniffi.bdk.BlockId
import uniffi.bdk.ChainPosition
import uniffi.bdk.ConfirmationBlockTime
import uniffi.bdk.Network
import uniffi.bdk.NoPointer
import uniffi.bdk.OutPoint
import uniffi.bdk.Script
import uniffi.bdk.Transaction
import uniffi.bdk.TxDetails
import uniffi.bdk.TxIn
import uniffi.bdk.TxOut
import uniffi.bdk.Txid
import uniffi.bdk.Wallet
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class BdkTransactionMapperV2ImplTests : FunSpec({
  val outgoingTransactionDetailDao = OutgoingTransactionDetailDaoMock(turbines::create)
  val mapper = BdkTransactionMapperV2Impl(outgoingTransactionDetailDao)

  val networkType = BitcoinNetworkType.BITCOIN
  val timestamp = someInstant
  val estimatedConfirmationTime = someInstant.plus(10.toDuration(DurationUnit.MINUTES))

  beforeTest {
    outgoingTransactionDetailDao.reset()
  }

  suspend fun createTransaction(
    txDetails: TxDetails,
    wallet: Wallet = TestWallet(),
  ): BitcoinTransaction =
    mapper.createTransaction(
      txDetails = txDetails,
      wallet = wallet,
      networkType = networkType
    )

  test("Confirmation status when unconfirmed") {
    createTransaction(makeTxDetails(chainPosition = ChainPosition.Unconfirmed(timestamp = null)))
      .shouldBePending()
  }

  test("Confirmation status when confirmed") {
    val confirmedPosition =
      ChainPosition.Confirmed(
        confirmationBlockTime =
          ConfirmationBlockTime(
            blockId = BlockId(height = 1u, hash = BlockHash(NoPointer)),
            confirmationTime = timestamp.epochSeconds.toULong()
          ),
        transitively = null
      )

    createTransaction(makeTxDetails(chainPosition = confirmedPosition))
      .confirmationStatus.shouldBe(
        BitcoinTransaction.ConfirmationStatus.Confirmed(
          blockTime = BlockTime(height = 1, timestamp = timestamp)
        )
      )
  }

  test("Broadcast and estimated confirmation times when dao has none") {
    val tx = createTransaction(makeTxDetails())
    tx.broadcastTime.shouldBeNull()
    tx.estimatedConfirmationTime.shouldBeNull()
  }

  test("Broadcast and estimated confirmation times when in dao") {
    val transactionId = DEFAULT_TXID
    outgoingTransactionDetailDao.insert(
      broadcastTime = timestamp,
      transactionId = transactionId,
      estimatedConfirmationTime = estimatedConfirmationTime,
      exchangeRates = null
    )
    outgoingTransactionDetailDao.insertCalls.awaitItem()

    val tx = createTransaction(makeTxDetails(txid = transactionId))
    tx.broadcastTime.shouldBe(timestamp)
    tx.estimatedConfirmationTime.shouldBe(estimatedConfirmationTime)
  }

  test("Receive-only transaction math and type") {
    val netReceiveValue = 400uL
    val feeValue = 21uL

    val tx =
      createTransaction(
        makeTxDetails(
          received = netReceiveValue,
          sent = 0uL,
          fee = feeValue
        )
      )

    tx.shouldBeIncoming()
    tx.total.shouldBe(BitcoinMoney.sats(netReceiveValue + feeValue))
    tx.subtotal.shouldBe(BitcoinMoney.sats(netReceiveValue))
  }

  test("Send transaction math and type") {
    val inputValue = 1_000uL
    val feeValue = 20uL
    val changeValue = inputValue - 580uL - feeValue
    val amountToSend = 580uL + feeValue

    val tx =
      createTransaction(
        makeTxDetails(
          received = changeValue,
          sent = inputValue,
          fee = feeValue
        )
      )

    tx.shouldBeOutgoing()
    tx.total.shouldBe(BitcoinMoney.sats(amountToSend))
    tx.subtotal.shouldBe(BitcoinMoney.sats(amountToSend - feeValue))
  }

  test("Transaction math and type when utxo consolidation") {
    val expectedAddress = bitcoinAddressP2WPKH.address
    val otherAddress = bitcoinAddressP2TR.address
    val script1 = scriptFromAddress(expectedAddress)
    val script2 = scriptFromAddress(otherAddress)

    val outputs =
      listOf(
        TxOut(value = Amount.fromSat(900uL), scriptPubkey = script1),
        TxOut(value = Amount.fromSat(100uL), scriptPubkey = script2)
      )

    val wallet = TestWallet(mineScripts = setOf(script1, script2))

    val tx =
      createTransaction(
        makeTxDetails(
          received = 900uL,
          sent = 1_000uL,
          fee = 100uL,
          tx = TestTransaction(outputs = outputs)
        ),
        wallet = wallet
      ).shouldBeUtxoConsolidation()

    tx.subtotal.shouldBe(BitcoinMoney.sats(900uL))
    tx.total.shouldBe(BitcoinMoney.sats(1_000uL))
    tx.recipientAddress.shouldNotBeNull().address.shouldBe(expectedAddress)
  }

  test("Recipient address extracted for incoming selects mine output") {
    val expectedAddress = bitcoinAddressP2TR.address
    val otherAddress = bitcoinAddressP2WPKH.address
    val scriptMine = scriptFromAddress(expectedAddress)
    val scriptNotMine = scriptFromAddress(otherAddress)

    val outputs =
      listOf(
        TxOut(value = Amount.fromSat(1uL), scriptPubkey = scriptNotMine),
        TxOut(value = Amount.fromSat(2uL), scriptPubkey = scriptMine)
      )

    val wallet = TestWallet(mineScripts = setOf(scriptMine))

    val tx =
      createTransaction(
        makeTxDetails(
          received = 100uL,
          sent = 0uL,
          tx = TestTransaction(outputs = outputs)
        ),
        wallet = wallet
      )

    tx.recipientAddress.shouldNotBeNull().address.shouldBe(expectedAddress)
    tx.shouldBeIncoming()
  }

  test("Recipient address extracted for outgoing selects not-mine output") {
    val expectedAddress = bitcoinAddressP2WPKH.address
    val changeAddress = bitcoinAddressP2TR.address
    val scriptNotMine = scriptFromAddress(expectedAddress)
    val scriptMine = scriptFromAddress(changeAddress)

    val outputs =
      listOf(
        TxOut(value = Amount.fromSat(1uL), scriptPubkey = scriptMine),
        TxOut(value = Amount.fromSat(2uL), scriptPubkey = scriptNotMine)
      )

    val wallet = TestWallet(mineScripts = setOf(scriptMine))

    val tx =
      createTransaction(
        makeTxDetails(
          received = 0uL,
          sent = 100uL,
          tx = TestTransaction(outputs = outputs)
        ),
        wallet = wallet
      ).shouldBeOutgoing()

    tx.recipientAddress.shouldNotBeNull().address.shouldBe(expectedAddress)
  }

  test("To address not determined when outputs empty") {
    val tx =
      createTransaction(
        makeTxDetails(
          received = 0uL,
          sent = 100uL,
          tx = TestTransaction(outputs = emptyList())
        )
      )

    tx.recipientAddress.shouldBeNull()
  }

  test("Maps inputs and outputs correctly") {
    val script = scriptFromAddress(bitcoinAddressP2WPKH.address)

    val inputs =
      listOf(
        TxIn(
          previousOutput = OutPoint(Txid.fromString("00".repeat(32)), 0u),
          scriptSig = Script(NoPointer),
          sequence = 0u,
          witness = emptyList()
        ),
        TxIn(
          previousOutput = OutPoint(Txid.fromString("11".repeat(32)), 1u),
          scriptSig = Script(NoPointer),
          sequence = 1u,
          witness = listOf(byteArrayOf(1, 2, 3))
        )
      )

    val outputs =
      listOf(
        TxOut(value = Amount.fromSat(5uL), scriptPubkey = script)
      )

    val wallet = TestWallet(mineScripts = setOf(script))

    val tx =
      createTransaction(
        makeTxDetails(
          received = 0uL,
          sent = 100uL,
          tx = TestTransaction(inputs = inputs, outputs = outputs)
        ),
        wallet = wallet
      )

    tx.inputs.size.shouldBe(2)
    tx.outputs.size.shouldBe(1)
    tx.inputs[1].outpoint.txid.shouldBe("11".repeat(32))
    tx.outputs[0].value.shouldBe(5uL)
  }
})

private const val DEFAULT_TXID =
  "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b"

private fun makeTxDetails(
  txid: String = DEFAULT_TXID,
  received: ULong = 0uL,
  sent: ULong = 2_000uL,
  fee: ULong? = null,
  chainPosition: ChainPosition = ChainPosition.Unconfirmed(timestamp = null),
  tx: Transaction = TestTransaction(),
): TxDetails =
  TxDetails(
    txid = Txid.fromString(txid),
    sent = Amount.fromSat(sent),
    received = Amount.fromSat(received),
    fee = fee?.let { Amount.fromSat(it) },
    feeRate = null,
    balanceDelta = 0,
    chainPosition = chainPosition,
    tx = tx
  )

private fun scriptFromAddress(address: String): Script =
  Address(address, Network.BITCOIN).scriptPubkey()

private class TestTransaction(
  private val inputs: List<TxIn> = emptyList(),
  private val outputs: List<TxOut> = emptyList(),
  private val weight: ULong = 0uL,
  private val vsize: ULong = 0uL,
) : Transaction(NoPointer) {
  override fun input(): List<TxIn> = inputs

  override fun output(): List<TxOut> = outputs

  override fun weight(): ULong = weight

  override fun vsize(): ULong = vsize
}

private class TestWallet(
  private val mineScripts: Set<Script> = emptySet(),
) : Wallet(NoPointer) {
  override fun isMine(script: Script): Boolean = mineScripts.contains(script)
}
