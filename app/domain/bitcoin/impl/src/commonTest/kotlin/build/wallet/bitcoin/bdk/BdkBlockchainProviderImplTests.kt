package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.*
import build.wallet.bitcoin.sync.DefaultServerSettingWithPreviousServerMock
import build.wallet.bitcoin.sync.ElectrumServerSettingProviderMock
import build.wallet.bitcoin.sync.UserDefinedServerSettingMock
import build.wallet.coroutines.turbine.turbines
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BdkBlockchainProviderImplTests : FunSpec({
  val bdkBlockchain =
    BdkBlockchainMock(
      blockHeightResult = BdkResult.Ok(1),
      blockHashResult = BdkResult.Ok("123"),
      broadcastResult = BdkResult.Ok(Unit),
      feeRateResult = BdkResult.Ok(1f),
      getTxResult = BdkResult.Ok(BdkTransactionMock(output = listOf(BdkTxOutMock)))
    )
  val bdkBlockchainFactory = BdkBlockchainFactoryMock(BdkResult.Ok(bdkBlockchain))
  val electrumServerSettingProvider = ElectrumServerSettingProviderMock(turbines::create)

  val provider = BdkBlockchainProviderImpl(bdkBlockchainFactory, electrumServerSettingProvider)

  beforeTest {
    provider.reset()
    electrumServerSettingProvider.reset()
    bdkBlockchainFactory.reset(BdkResult.Ok(bdkBlockchain))
  }

  test("initialize new blockchain instance when there is no cache") {
    provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))
  }

  test("reuse cached blockchain instance") {
    provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))

    // Different instance.
    val newBdkBlockchain =
      bdkBlockchain.copy(
        blockHeightResult = BdkResult.Ok(2)
      )
    bdkBlockchainFactory.blockchainResult = BdkResult.Ok(newBdkBlockchain)

    // Reuse existing cache instance
    provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))
  }

  test("fail to initialize blockchain instance") {
    val bdkBlockchainErrResult = BdkResult.Err<BdkBlockchain>(someBdkError)
    bdkBlockchainFactory.blockchainResult = bdkBlockchainErrResult

    provider.blockchain().shouldBe(bdkBlockchainErrResult)
  }

  test("fail to initialize blockchain instance after retry") {
    val bdkBlockchainErrResult = BdkResult.Err<BdkBlockchain>(someBdkError)
    bdkBlockchainFactory.blockchainResult = bdkBlockchainErrResult

    provider.blockchain().shouldBe(bdkBlockchainErrResult)
    provider.blockchain().shouldBe(bdkBlockchainErrResult)
  }

  test("successfully initialize blockchain instance after retry") {
    val bdkBlockchainErrResult = BdkResult.Err<BdkBlockchain>(someBdkError)
    bdkBlockchainFactory.blockchainResult = bdkBlockchainErrResult
    provider.blockchain().shouldBe(bdkBlockchainErrResult)

    bdkBlockchainFactory.blockchainResult = BdkResult.Ok(bdkBlockchain)

    provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))
  }

  test("return new blockchain instance after electrum server preference changes") {
    provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))

    // Different instance.
    val newBdkBlockchain =
      bdkBlockchain.copy(
        blockHeightResult = BdkResult.Ok(2)
      )
    bdkBlockchainFactory.blockchainResult = BdkResult.Ok(newBdkBlockchain)
    electrumServerSettingProvider.electrumServerSetting.value = UserDefinedServerSettingMock

    // Use new instance created by the factory
    provider.blockchain().shouldBe(BdkResult.Ok(newBdkBlockchain))
  }

  test(
    "returns blockchain instance connected to default, even if a previous user-defined electrum preference was set"
  ) {
    provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))

    // Different instance.
    val newBdkBlockchain =
      bdkBlockchain.copy(
        blockHeightResult = BdkResult.Ok(2)
      )
    bdkBlockchainFactory.blockchainResult = BdkResult.Ok(newBdkBlockchain)
    electrumServerSettingProvider.electrumServerSetting.value = UserDefinedServerSettingMock

    // Use new instance created by the factory
    provider.blockchain().shouldBe(BdkResult.Ok(newBdkBlockchain))

    // Now, switch back to default.
    bdkBlockchainFactory.blockchainResult = BdkResult.Ok(bdkBlockchain)
    electrumServerSettingProvider.electrumServerSetting.value = DefaultServerSettingWithPreviousServerMock

    // Provider should know that we have swapped underlying Electrum server to connect and update
    // its cache.
    provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))
  }
})
