package build.wallet.bitcoin.blockchain

import build.wallet.bdk.bindings.*
import build.wallet.bitcoin.bdk.BdkBlockchainProviderMock
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.time.ClockFake
import build.wallet.time.someInstant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BitcoinBlockchainImplTests : FunSpec({
  val broadcastTime = someInstant
  val expectedTxid = "test-txid-12345"
  val expectedBlockHeight = 850000L
  val expectedBlockHash = "00000000000000000002a7c4c1e48d76c5a37902165a270156b7a8d72728a054"
  val expectedTransaction = BdkTransactionMock(output = listOf(BdkTxOutMock))
  val bdkPsbt = BdkPartiallySignedTransactionMock(PsbtMock.id)

  val bdkBlockchainMock = BdkBlockchainMock(
    blockHeightResult = BdkResult.Ok(expectedBlockHeight),
    blockHashResult = BdkResult.Ok(expectedBlockHash),
    broadcastResult = BdkResult.Ok(expectedTxid),
    feeRateResult = BdkResult.Ok(1f),
    getTxResult = BdkResult.Ok(expectedTransaction)
  )

  val bdkBlockchainProvider = BdkBlockchainProviderMock(
    turbines::create,
    blockchainResult = BdkResult.Ok(bdkBlockchainMock)
  )

  val bitcoinBlockchain =
    BitcoinBlockchainImpl(
      bdkBlockchainProvider = bdkBlockchainProvider,
      bdkPsbtBuilder = BdkPartiallySignedTransactionBuilderMock(psbt = bdkPsbt),
      clock = ClockFake(now = broadcastTime)
    )

  beforeTest {
    bdkBlockchainProvider.reset()
    bdkBlockchainProvider.blockchainResult = BdkResult.Ok(bdkBlockchainMock)
  }

  test("broadcasting transaction uses BDK 2 ElectrumClient and returns txid") {
    val result = bitcoinBlockchain.broadcast(PsbtMock)

    bdkBlockchainProvider.blockchainCalls.awaitItem()

    result.isOk.shouldBe(true)
    result.value.transactionId.shouldBe(expectedTxid)
    result.value.broadcastTime.shouldBe(broadcastTime)
  }

  test("getLatestBlockHeight returns block height from blockchain") {
    val result = bitcoinBlockchain.getLatestBlockHeight()

    bdkBlockchainProvider.blockchainCalls.awaitItem()

    result.isOk.shouldBe(true)
    result.value.shouldBe(expectedBlockHeight)
  }

  test("getLatestBlockHash returns block hash at latest height") {
    val result = bitcoinBlockchain.getLatestBlockHash()

    bdkBlockchainProvider.blockchainCalls.awaitItem()

    result.isOk.shouldBe(true)
    result.value.shouldBe(expectedBlockHash)
  }

  test("getTx returns transaction for given txid") {
    val txid = BitcoinTransactionId(expectedTxid)

    val result = bitcoinBlockchain.getTx(txid)

    bdkBlockchainProvider.blockchainCalls.awaitItem()

    result.isOk.shouldBe(true)
    result.value.shouldBe(expectedTransaction)
  }
})
