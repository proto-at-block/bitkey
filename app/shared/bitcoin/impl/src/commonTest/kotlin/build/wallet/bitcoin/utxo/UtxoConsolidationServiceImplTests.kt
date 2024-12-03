package build.wallet.bitcoin.utxo

import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bdk.bindings.BdkUtxoMock
import build.wallet.bdk.bindings.BdkUtxoMock2
import build.wallet.bitcoin.address.BitcoinAddressServiceFake
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.bitcoin.utxo.UtxoConsolidationType.ConsolidateAll
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.UtxoMaxConsolidationCountFeatureFlag
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.testing.shouldBeErr
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class UtxoConsolidationServiceImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val bitcoinAddressService = BitcoinAddressServiceFake()
  val bitcoinFeeRateEstimator = BitcoinFeeRateEstimatorMock()
  val spendingWallet = SpendingWalletMock(turbines::create)
  val utxoMaxConsolidationCountFeatureFlag = UtxoMaxConsolidationCountFeatureFlag(FeatureFlagDaoFake())

  val utxoConsolidationService = UtxoConsolidationServiceImpl(
    accountService = accountService,
    bitcoinWalletService = bitcoinWalletService,
    bitcoinAddressService = bitcoinAddressService,
    bitcoinFeeRateEstimator = bitcoinFeeRateEstimator,
    utxoMaxConsolidationCountFeatureFlag = utxoMaxConsolidationCountFeatureFlag
  )

  beforeTest {
    accountService.reset()
    bitcoinWalletService.reset()
    bitcoinAddressService.reset()
    utxoMaxConsolidationCountFeatureFlag.reset()

    utxoMaxConsolidationCountFeatureFlag.setFlagValue(FeatureFlagValue.DoubleFlag(150.0))
    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      utxos = Utxos(
        confirmed = setOf(
          BdkUtxoMock,
          BdkUtxoMock2
        ),
        unconfirmed = setOf()
      )
    )
    bitcoinWalletService.spendingWallet.value = spendingWallet
  }

  test("prepareUtxoConsolidation happy path") {
    val consolidationParams = utxoConsolidationService.prepareUtxoConsolidation().value.single()
    consolidationParams.should {
      it.type.shouldBe(ConsolidateAll)
      it.balance.shouldBe(sats(3L))
      it.eligibleUtxoCount.shouldBe(2)
      it.consolidationCost.isPositive.shouldBeTrue()
      it.walletHasUnconfirmedUtxos.shouldBeFalse()
      it.walletExceedsMaxUtxoCount.shouldBeFalse()
      it.maxUtxoCount.shouldBe(150)
    }
  }

  test("utxos above maximum amount") {
    // A set of 200 UTXOs, each with a value of 2.
    val confirmedUtxos = List(200) {
      BdkUtxoMock2.copy(
        outPoint = BdkUtxoMock2.outPoint.copy(
          txid = it.toString()
        )
      )
    }.toSet()

    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      utxos = Utxos(
        confirmed = confirmedUtxos + setOf(BdkUtxoMock), // Adds a UTXO with value of 1
        unconfirmed = setOf(BdkUtxoMock2)
      )
    )

    val consolidationParams = utxoConsolidationService.prepareUtxoConsolidation().value.single()
    consolidationParams.should {
      it.type.shouldBe(ConsolidateAll)
      it.balance.shouldBe(sats(299L))
      it.eligibleUtxoCount.shouldBe(150)
      it.consolidationCost.isPositive.shouldBeTrue()
      it.walletHasUnconfirmedUtxos.shouldBeTrue()
      it.walletExceedsMaxUtxoCount.shouldBeTrue()
      it.maxUtxoCount.shouldBe(150)
    }
  }

  test("utxo max == 0 does not filter") {
    utxoMaxConsolidationCountFeatureFlag.setFlagValue(FeatureFlagValue.DoubleFlag(0.0))
    val consolidationParams = utxoConsolidationService.prepareUtxoConsolidation().value.single()
    consolidationParams.should {
      it.type.shouldBe(ConsolidateAll)
      it.balance.shouldBe(sats(3L))
      it.eligibleUtxoCount.shouldBe(2)
      it.consolidationCost.isPositive.shouldBeTrue()
      it.walletHasUnconfirmedUtxos.shouldBeFalse()
      it.walletExceedsMaxUtxoCount.shouldBeFalse()
      it.maxUtxoCount.shouldBe(0)
    }
  }

  test("no confirmed utxos") {
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      utxos = Utxos(
        confirmed = setOf(),
        unconfirmed = setOf(
          BdkUtxoMock,
          BdkUtxoMock2
        )
      )
    )

    val consolidationParams = utxoConsolidationService.prepareUtxoConsolidation()
    consolidationParams.shouldBeErr(NotEnoughUtxosToConsolidateError(utxoCount = 0))
  }
})
