package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.*
import build.wallet.bitcoin.sync.DefaultServerSettingWithPreviousServerMock
import build.wallet.bitcoin.sync.ElectrumServerSettingProviderMock
import build.wallet.bitcoin.sync.UserDefinedServerSettingMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.feature.flags.Bdk2FeatureFlag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BdkBlockchainProviderImplTests : FunSpec({
  val bdkBlockchain =
    BdkBlockchainMock(
      blockHeightResult = BdkResult.Ok(1),
      blockHashResult = BdkResult.Ok("123"),
      broadcastResult = BdkResult.Ok("test-txid"),
      feeRateResult = BdkResult.Ok(1f),
      getTxResult = BdkResult.Ok(BdkTransactionMock(output = listOf(BdkTxOutMock)))
    )
  val legacyBdkBlockchain =
    BdkBlockchainMock(
      blockHeightResult = BdkResult.Ok(100),
      blockHashResult = BdkResult.Ok("legacy-hash"),
      broadcastResult = BdkResult.Ok("legacy-txid"),
      feeRateResult = BdkResult.Ok(2f),
      getTxResult = BdkResult.Ok(BdkTransactionMock(output = listOf(BdkTxOutMock)))
    )
  val bdkBlockchainFactory = BdkBlockchainFactoryMock(BdkResult.Ok(bdkBlockchain))
  val legacyBdkBlockchainFactory = LegacyBdkBlockchainFactoryMock(BdkResult.Ok(legacyBdkBlockchain))
  val featureFlagDao = FeatureFlagDaoFake()
  val bdk2FeatureFlag = Bdk2FeatureFlag(featureFlagDao)
  val electrumServerSettingProvider = ElectrumServerSettingProviderMock(turbines::create)

  val provider = BdkBlockchainProviderImpl(
    bdkBlockchainFactory,
    legacyBdkBlockchainFactory,
    bdk2FeatureFlag,
    electrumServerSettingProvider
  )

  beforeTest {
    provider.reset()
    electrumServerSettingProvider.reset()
    featureFlagDao.reset()
    bdk2FeatureFlag.reset()
    bdkBlockchainFactory.reset(BdkResult.Ok(bdkBlockchain))
    legacyBdkBlockchainFactory.reset(BdkResult.Ok(legacyBdkBlockchain))
  }

  context("with BDK 2 feature flag disabled") {
    test("initialize new blockchain instance uses legacy BDK when there is no cache") {
      provider.blockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))
    }

    test("reuse cached blockchain instance with legacy BDK") {
      provider.blockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))

      // Different instance from legacy factory.
      val newLegacyBlockchain =
        legacyBdkBlockchain.copy(
          blockHeightResult = BdkResult.Ok(200)
        )
      legacyBdkBlockchainFactory.blockchainResult = BdkResult.Ok(newLegacyBlockchain)

      // Reuse existing cache instance
      provider.blockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))
    }

    test("fail to initialize blockchain instance with legacy BDK") {
      val bdkBlockchainErrResult = BdkResult.Err<BdkBlockchain>(someBdkError)
      legacyBdkBlockchainFactory.blockchainResult = bdkBlockchainErrResult

      provider.blockchain().shouldBe(bdkBlockchainErrResult)
    }

    test("fail to initialize blockchain instance after retry with legacy BDK") {
      val bdkBlockchainErrResult = BdkResult.Err<BdkBlockchain>(someBdkError)
      legacyBdkBlockchainFactory.blockchainResult = bdkBlockchainErrResult

      provider.blockchain().shouldBe(bdkBlockchainErrResult)
      provider.blockchain().shouldBe(bdkBlockchainErrResult)
    }

    test("successfully initialize blockchain instance after retry with legacy BDK") {
      val bdkBlockchainErrResult = BdkResult.Err<BdkBlockchain>(someBdkError)
      legacyBdkBlockchainFactory.blockchainResult = bdkBlockchainErrResult
      provider.blockchain().shouldBe(bdkBlockchainErrResult)

      legacyBdkBlockchainFactory.blockchainResult = BdkResult.Ok(legacyBdkBlockchain)

      provider.blockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))
    }

    test("return new blockchain instance after electrum server preference changes with legacy BDK") {
      provider.blockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))

      // Different instance
      val newLegacyBlockchain =
        legacyBdkBlockchain.copy(
          blockHeightResult = BdkResult.Ok(200)
        )
      legacyBdkBlockchainFactory.blockchainResult = BdkResult.Ok(newLegacyBlockchain)
      electrumServerSettingProvider.electrumServerSetting.value = UserDefinedServerSettingMock

      // Use new instance created by the factory
      provider.blockchain().shouldBe(BdkResult.Ok(newLegacyBlockchain))
    }
  }

  context("with BDK 2 feature flag enabled") {
    beforeTest {
      bdk2FeatureFlag.setFlagValue(BooleanFlag(true))
    }

    test("initialize new blockchain instance uses BDK 2 when there is no cache") {
      provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))
    }

    test("reuse cached blockchain instance with BDK 2") {
      provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))

      // Different instance from BDK 2 factory
      val newBdkBlockchain =
        bdkBlockchain.copy(
          blockHeightResult = BdkResult.Ok(2)
        )
      bdkBlockchainFactory.blockchainResult = BdkResult.Ok(newBdkBlockchain)

      // Reuse existing cache instance
      provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))
    }

    test("fail to initialize blockchain instance with BDK 2") {
      val bdkBlockchainErrResult = BdkResult.Err<BdkBlockchain>(someBdkError)
      bdkBlockchainFactory.blockchainResult = bdkBlockchainErrResult

      provider.blockchain().shouldBe(bdkBlockchainErrResult)
    }

    test("return new blockchain instance after electrum server preference changes with BDK 2") {
      provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))

      // Different instance
      val newBdkBlockchain =
        bdkBlockchain.copy(
          blockHeightResult = BdkResult.Ok(2)
        )
      bdkBlockchainFactory.blockchainResult = BdkResult.Ok(newBdkBlockchain)
      electrumServerSettingProvider.electrumServerSetting.value = UserDefinedServerSettingMock

      // Use new instance created by the factory
      provider.blockchain().shouldBe(BdkResult.Ok(newBdkBlockchain))
    }
  }

  context("switching BDK version via feature flag") {
    test("invalidates cache when feature flag changes from off to on") {
      provider.blockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))

      bdk2FeatureFlag.setFlagValue(BooleanFlag(true))

      provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))
    }

    test("invalidates cache when feature flag changes from on to off") {
      bdk2FeatureFlag.setFlagValue(BooleanFlag(true))
      provider.blockchain().shouldBe(BdkResult.Ok(bdkBlockchain))

      bdk2FeatureFlag.setFlagValue(BooleanFlag(false))

      provider.blockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))
    }
  }

  context("legacyBlockchain for wallet sync") {
    test("returns legacy blockchain regardless of feature flag") {
      // Even with BDK 2 enabled, legacyBlockchain() should return legacy
      bdk2FeatureFlag.setFlagValue(BooleanFlag(true))

      provider.legacyBlockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))
    }

    test("caches and reuses legacy blockchain instance") {
      provider.legacyBlockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))

      // Different instance from legacy factory
      val newLegacyBlockchain = legacyBdkBlockchain.copy(blockHeightResult = BdkResult.Ok(200))
      legacyBdkBlockchainFactory.blockchainResult = BdkResult.Ok(newLegacyBlockchain)

      // Reuse existing cached instance
      provider.legacyBlockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))
    }

    test("invalidates cache when electrum server changes") {
      provider.legacyBlockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))

      // Different instance and server change
      val newLegacyBlockchain = legacyBdkBlockchain.copy(blockHeightResult = BdkResult.Ok(200))
      legacyBdkBlockchainFactory.blockchainResult = BdkResult.Ok(newLegacyBlockchain)
      electrumServerSettingProvider.electrumServerSetting.value = UserDefinedServerSettingMock

      // Should use new instance due to server change
      provider.legacyBlockchain().shouldBe(BdkResult.Ok(newLegacyBlockchain))
    }

    test("fails to initialize legacy blockchain") {
      val bdkBlockchainErrResult = BdkResult.Err<BdkBlockchain>(someBdkError)
      legacyBdkBlockchainFactory.blockchainResult = bdkBlockchainErrResult

      provider.legacyBlockchain().shouldBe(bdkBlockchainErrResult)
    }
  }

  test(
    "returns blockchain instance connected to default, even if a previous user-defined electrum preference was set"
  ) {
    provider.blockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))

    // Different instance
    val newLegacyBlockchain =
      legacyBdkBlockchain.copy(
        blockHeightResult = BdkResult.Ok(200)
      )
    legacyBdkBlockchainFactory.blockchainResult = BdkResult.Ok(newLegacyBlockchain)
    electrumServerSettingProvider.electrumServerSetting.value = UserDefinedServerSettingMock

    // Use new instance created by the factory
    provider.blockchain().shouldBe(BdkResult.Ok(newLegacyBlockchain))

    // Switch back to default
    legacyBdkBlockchainFactory.blockchainResult = BdkResult.Ok(legacyBdkBlockchain)
    electrumServerSettingProvider.electrumServerSetting.value = DefaultServerSettingWithPreviousServerMock

    // Provider should know that we have swapped underlying Electrum server to connect and update
    // its cache.
    provider.blockchain().shouldBe(BdkResult.Ok(legacyBdkBlockchain))
  }
})
