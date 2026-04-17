@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.bitcoin.wallet

import app.cash.turbine.test
import build.wallet.bdk.bindings.BdkAddressBuilderMock
import build.wallet.bdk.bindings.BdkOutPointMock
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilderMock
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.bdk.*
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.FeeBumpAllowShrinkingCheckerFake
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod.*
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.testing.shouldBeErrOfType
import build.wallet.toUByteList
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Duration.Companion.milliseconds

class SpendingWalletImplTests : FunSpec({

  val bdkWallet = BdkWalletMock(turbines::create)
  val bdkWalletSyncer = BdkWalletSyncerMock(turbines::create)
  val bdkAddressBuilder = BdkAddressBuilderMock(turbines::create)
  val appSessionManager = AppSessionManagerFake()
  val bitcoinFeeRateEstimator = BitcoinFeeRateEstimatorMock()
  val bdkBumpFeeTxBuilder = BdkBumpFeeTxBuilderMock()
  val feeBumpAllowShrinkingChecker = FeeBumpAllowShrinkingCheckerFake()
  val syncFrequency = 100.milliseconds

  fun buildWallet(syncScope: CoroutineScope) =
    SpendingWalletImpl(
      identifier = "wallet-identifier",
      bdkWallet = bdkWallet,
      networkType = BitcoinNetworkType.BITCOIN,
      bdkTransactionMapper = BdkTransactionMapperMock(),
      bdkWalletSyncer = bdkWalletSyncer,
      bdkPsbtBuilder = BdkPartiallySignedTransactionBuilderMock(),
      bdkTxBuilderFactory = BdkTxBuilderFactoryMock(BdkTxBuilderMock()),
      bdkAddressBuilder = bdkAddressBuilder,
      bdkBumpFeeTxBuilderFactory = BdkBumpFeeTxBuilderFactoryMock(bdkBumpFeeTxBuilder),
      appSessionManager = appSessionManager,
      syncContext = syncScope.coroutineContext,
      bitcoinFeeRateEstimator = bitcoinFeeRateEstimator,
      feeBumpAllowShrinkingChecker = feeBumpAllowShrinkingChecker
    )

  beforeEach {
    appSessionManager.reset()
    feeBumpAllowShrinkingChecker.reset()
    bdkBumpFeeTxBuilder.reset()
  }

  test("Balance initialization") {
    val wallet = buildWallet(createBackgroundScope())
    wallet.balance().test {
      wallet.initializeBalanceAndTransactions()
      awaitItem().shouldBe(BitcoinBalance.ZeroBalance)
    }
  }

  test("Transactions initialization") {
    val wallet = buildWallet(createBackgroundScope())
    wallet.transactions().test {
      wallet.initializeBalanceAndTransactions()
      awaitItem().shouldBeEmpty()
    }
  }

  test("syncs do not occur while app is backgrounded") {
    val backgroundScope = createBackgroundScope()
    val wallet = buildWallet(backgroundScope)
    appSessionManager.appDidEnterBackground()
    backgroundScope.launch {
      wallet.launchPeriodicSync(scope = this, interval = syncFrequency)
    }
    delay(syncFrequency)
    bdkWalletSyncer.syncCalls.awaitNoEvents()

    appSessionManager.appDidEnterForeground()

    bdkWalletSyncer.syncCalls.awaitItem()
  }

  test("speed up psbt sets allow_shrinking if enabled") {
    val bdkScript = BdkScriptMock("blah".encodeUtf8().toUByteList())
    feeBumpAllowShrinkingChecker.shrinkingOutput = bdkScript

    val wallet = buildWallet(createBackgroundScope())
    wallet.createSignedPsbt(
      constructionType = FeeBump(
        txid = "some-txid",
        feeRate = FeeRate(1f)
      )
    )

    bdkBumpFeeTxBuilder.allowShrinkingScript
      .shouldNotBeNull()
      .shouldBe(bdkScript)
  }

  test("speed up psbt does not set allow_shrinking if disabled") {
    feeBumpAllowShrinkingChecker.shrinkingOutput = null

    val wallet = buildWallet(createBackgroundScope())
    wallet.createSignedPsbt(
      constructionType = FeeBump(
        txid = "some-txid",
        feeRate = FeeRate(1f)
      )
    )

    bdkBumpFeeTxBuilder.allowShrinkingScript
      .shouldBeNull()
  }

  test("fee bump with drain is rejected for legacy spending wallet") {
    val wallet = buildWallet(createBackgroundScope())
    val result = wallet.createSignedPsbt(
      constructionType = FeeBumpWithDrain(
        txid = "some-txid",
        feeRate = FeeRate(1f),
        drainToScript = BdkScriptMock()
      )
    )

    result.shouldBeErrOfType<Error>()
      .message
      .shouldBe("FeeBumpWithDrain is not supported in legacy SpendingWalletImpl")
  }

  test("cpfp with MinRelayRate builds psbt successfully") {
    val wallet = buildWallet(createBackgroundScope())
    val result = wallet.createSignedPsbt(
      constructionType = Cpfp(
        utxoOutpoint = BdkOutPointMock,
        recipientAddress = someBitcoinAddress,
        feePolicy = FeePolicy.MinRelayRate
      )
    )

    result.isOk.shouldBe(true)
  }

  test("cpfp with Rate fee policy builds psbt successfully") {
    val wallet = buildWallet(createBackgroundScope())
    val result = wallet.createSignedPsbt(
      constructionType = Cpfp(
        utxoOutpoint = BdkOutPointMock,
        recipientAddress = someBitcoinAddress,
        feePolicy = FeePolicy.Rate(FeeRate(5f))
      )
    )

    result.isOk.shouldBe(true)
  }
})
