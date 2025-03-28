package build.wallet.bitcoin.blockchain

import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilderMock
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionMock
import build.wallet.bitcoin.bdk.BdkBlockchainProviderMock
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.time.ClockFake
import build.wallet.time.someInstant
import io.kotest.core.spec.style.FunSpec

class BitcoinBlockchainImplTests : FunSpec({
  val broadcastTime = someInstant
  val bdkPsbt = BdkPartiallySignedTransactionMock(PsbtMock.id)
  val bdkBlockchainProvider = BdkBlockchainProviderMock(turbines::create)

  val broadcaster =
    BitcoinBlockchainImpl(
      bdkBlockchainProvider = bdkBlockchainProvider,
      bdkPsbtBuilder = BdkPartiallySignedTransactionBuilderMock(psbt = bdkPsbt),
      clock = ClockFake(now = broadcastTime)
    )

  test("broadcasting transaction adds time to details dao") {
    broadcaster.broadcast(PsbtMock)
    bdkBlockchainProvider.blockchainCalls.awaitItem()
  }
})
