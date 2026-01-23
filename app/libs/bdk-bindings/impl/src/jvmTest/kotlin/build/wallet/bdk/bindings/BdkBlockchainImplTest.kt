package build.wallet.bdk.bindings

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.mapBoth
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import uniffi.bdk.Transaction
import uniffi.bdk.Txid

class BdkBlockchainImplTest : FunSpec({

  val electrum = BdkElectrumClientFake()
  val blockchain = BdkBlockchainImpl(electrum)

  test("broadcast wraps txid as string") {
    val txBytes = TestTransactions.genesisTransactionBytes()
    electrum.broadcastResult = Txid.fromString(BdkElectrumClientFake.DEFAULT_TXID)

    val tx = BdkTransactionImpl(Transaction(txBytes))
    val result = blockchain.broadcastBlocking(tx)

    result.result.shouldBe(Ok(BdkElectrumClientFake.DEFAULT_TXID))
    electrum.broadcastedTransactions.single().serialize().toList().map { it.toUByte() }
      .shouldBe(txBytes.map { it.toUByte() })
  }

  test("estimateFee converts btc/kB to sats/vB") {
    electrum.estimateFeeResult = 0.0001 // 10_000 sats/kB => 10 sats/vB

    val fee = blockchain.estimateFeeBlocking(targetBlocks = 1u)

    fee.result.shouldBe(Ok(10f))
  }

  test("estimateFee returns FeeRateUnavailable for invalid rate") {
    electrum.estimateFeeResult = Double.NaN

    val result = blockchain.estimateFeeBlocking(1u).result
    val error = result.mapBoth(
      success = { error("expected failure") },
      failure = { it }
    )
    error.shouldBeInstanceOf<BdkError.FeeRateUnavailable>()
    error.message.shouldBe("Electrum returned invalid fee rate: NaN")
  }

  test("getHeight returns latest block height") {
    electrum.latestBlockHeightResult = 850_000L

    val result = blockchain.getHeightBlocking()

    result.result.shouldBe(Ok(850_000L))
  }

  test("getHeight propagates errors as BdkError") {
    electrum.latestBlockHeightError = RuntimeException("connection failed")

    val result = blockchain.getHeightBlocking().result
    val error = result.mapBoth(
      success = { error("expected failure") },
      failure = { it }
    )
    error.shouldBeInstanceOf<BdkError.Generic>()
    error.message.shouldBe("connection failed")
  }

  test("getBlockHash returns hash for height") {
    val expectedHash = "00000000000000000002a7c4c1e48d76c5a37902165a270156b7a8d72728a054"
    electrum.blockHashes[850_000UL] = expectedHash

    val result = blockchain.getBlockHashBlocking(850_000L)

    result.result.shouldBe(Ok(expectedHash))
  }

  test("getBlockHash propagates errors as BdkError") {
    electrum.blockHashError = RuntimeException("connection failed")

    val result = blockchain.getBlockHashBlocking(999_999_999L).result
    val error = result.mapBoth(
      success = { error("expected failure") },
      failure = { it }
    )
    error.shouldBeInstanceOf<BdkError.Generic>()
    error.message.shouldBe("connection failed")
  }

  test("estimateFee returns FeeRateUnavailable for zero rate") {
    electrum.estimateFeeResult = 0.0

    val result = blockchain.estimateFeeBlocking(1u).result
    val error = result.mapBoth(
      success = { error("expected failure") },
      failure = { it }
    )
    error.shouldBeInstanceOf<BdkError.FeeRateUnavailable>()
  }

  test("estimateFee returns FeeRateUnavailable for negative rate") {
    electrum.estimateFeeResult = -1.0

    val result = blockchain.estimateFeeBlocking(1u).result
    val error = result.mapBoth(
      success = { error("expected failure") },
      failure = { it }
    )
    error.shouldBeInstanceOf<BdkError.FeeRateUnavailable>()
  }
})
